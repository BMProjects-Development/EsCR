package com.algorithmlx.ecr.api.vfx.gltf

import com.algorithmlx.ecr.api.vfx.array
import com.algorithmlx.ecr.api.vfx.bool
import com.algorithmlx.ecr.api.vfx.number
import com.algorithmlx.ecr.api.vfx.obj
import com.algorithmlx.ecr.api.vfx.string
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.resources.Identifier
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64

internal class VfxGltfDocument(val id: Identifier, bytes: ByteArray, private val resource: (Identifier) -> ByteArray) {
    private val chunks = chunks(bytes)
    val root = Json.parseToJsonElement(chunks.first.toString(Charsets.UTF_8).trimEnd('\u0000', ' ', '\n', '\r')).obj()
    private val buffers = root.array("buffers").map { raw ->
        val buffer = raw.obj()
        val data = buffer.string("uri").takeIf(String::isNotBlank)?.let(::bytes) ?: requireNotNull(chunks.second) { "Missing glTF buffer URI" }
        require(data.size >= buffer.number("byteLength").toInt()) { "Truncated glTF buffer" }
        ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
    }
    private val accessors = hashMapOf<Int, FloatArray>()

    init {
        require(root.obj("asset").string("version").startsWith("2.")) { "Only glTF 2 is supported" }
        val supported = setOf("KHR_texture_transform", "KHR_materials_unlit")
        require(root.array("extensionsRequired").all { it is JsonPrimitive && it.content in supported }) { "Required glTF extensions need an explicit decoder: ${root["extensionsRequired"]}" }
    }

    fun resolve(uri: String): Identifier {
        if (':' in uri) return Identifier.parse(uri)
        val base = "/" + id.path.substringBeforeLast('/', "") + "/"
        val resolved = URI(base).resolve(uri).normalize()
        require(resolved.scheme == null && resolved.authority == null && !resolved.path.startsWith("/../")) { "Invalid glTF resource URI: $uri" }
        return Identifier.fromNamespaceAndPath(id.namespace, resolved.path.removePrefix("/"))
    }

    fun bytes(uri: String): ByteArray = if (uri.startsWith("data:")) {
        require(uri.substringBefore(',').endsWith(";base64")) { "Expected base64 glTF data URI" }
        Base64.getDecoder().decode(uri.substringAfter(','))
    }
    else
        resource(resolve(uri))

    fun view(index: Int): ByteArray {
        val view = root.array("bufferViews")[index].obj()
        val buffer = buffers[view.number("buffer").toInt()].duplicate()
        val start = view.number("byteOffset").toInt()
        val count = view.number("byteLength").toInt()
        require(start >= 0 && count >= 0 && start + count <= buffer.limit()) { "Invalid glTF buffer view" }
        return ByteArray(count).also { buffer.position(start).get(it) }
    }

    fun dimensions(index: Int): Int = dimensions(root.array("accessors")[index].obj().string("type"))

    fun accessor(index: Int): FloatArray = accessors.getOrPut(index) {
        val spec = root.array("accessors")[index].obj()
        val dimensions = dimensions(spec.string("type"))
        val count = spec.number("count").toInt()
        require(count in 0..1000000) { "Invalid glTF accessor count" }
        val values = FloatArray(count * dimensions)
        if ("bufferView" in spec) read(spec.number("bufferView").toInt(), spec.number("byteOffset").toInt(), spec, count, dimensions, values)
        val sparse = spec.obj("sparse")
        if (sparse.isNotEmpty()) {
            val entries = sparse.number("count").toInt()
            require(entries in 0..count) { "Invalid sparse glTF count" }
            val indices = sparse.obj("indices")
            val indicesBuffer = ByteBuffer.wrap(view(indices.number("bufferView").toInt())).order(ByteOrder.LITTLE_ENDIAN)
            val indexType = indices.number("componentType").toInt()
            require(indexType in setOf(5121, 5123, 5125)) { "Invalid sparse glTF index type" }
            val replacements = FloatArray(entries * dimensions)
            val data = sparse.obj("values")
            read(data.number("bufferView").toInt(), data.number("byteOffset").toInt(), spec, entries, dimensions, replacements)
            repeat(entries) { i ->
                val at = component(indicesBuffer, indices.number("byteOffset").toInt() + i * size(indexType), indexType, false).toInt()
                require(at in 0 until count) { "Sparse glTF index is out of bounds" }
                replacements.copyInto(values, at * dimensions, i * dimensions, (i + 1) * dimensions)
            }
        }
        require(values.all(Float::isFinite)) { "Non-finite glTF accessor" }
        values
    }

    private fun read(viewIndex: Int, offset: Int, spec: JsonObject, count: Int, dimensions: Int, target: FloatArray) {
        val view = root.array("bufferViews")[viewIndex].obj()
        val data = ByteBuffer.wrap(view(viewIndex)).order(ByteOrder.LITTLE_ENDIAN)
        val type = spec.number("componentType").toInt()
        val componentBytes = size(type)
        val columns = if (spec.string("type").startsWith("MAT")) spec.string("type").last().digitToInt() else 1
        val rows = dimensions / columns
        val columnBytes = if (columns > 1) ((rows * componentBytes + 3) / 4) * 4 else rows * componentBytes
        val stride = view.number("byteStride", (columns * columnBytes).toFloat()).toInt()
        require(stride >= columns * columnBytes && offset >= 0) { "Invalid glTF accessor stride" }
        repeat(count) { element -> repeat(dimensions) { component ->
            val at = offset + element * stride + (component / rows) * columnBytes + (component % rows) * componentBytes
            target[element * dimensions + component] = component(data, at, type, spec.bool("normalized"))
        } }
    }

    private fun component(data: ByteBuffer, offset: Int, type: Int, normalized: Boolean): Float {
        require(offset >= 0 && offset + size(type) <= data.limit()) { "Truncated glTF accessor" }
        val value = when (type) {
            5120 -> data.get(offset).toFloat()
            5121 -> (data.get(offset).toInt() and 255).toFloat()
            5122 -> data.getShort(offset).toFloat()
            5123 -> (data.getShort(offset).toInt() and 65535).toFloat()
            5125 -> (data.getInt(offset).toLong() and 0xffffffffL).toFloat()
            5126 -> data.getFloat(offset)
            else -> error("Unsupported glTF component type: $type")
        }
        if (!normalized || type == 5126) return value
        return when (type) {
            5120 -> (value / 127f).coerceAtLeast(-1f)
            5121 -> value / 255f
            5122 -> (value / 32767f).coerceAtLeast(-1f)
            5123 -> value / 65535f
            else -> error("Invalid normalized glTF component type")
        }
    }

    private fun dimensions(type: String): Int = when (type) {
        "SCALAR" -> 1
        "VEC2", "VEC3", "VEC4" -> type.last().digitToInt()
        "MAT2", "MAT3", "MAT4" -> type.last().digitToInt().let { it * it }
        else -> error("Unsupported glTF accessor type: $type")
    }

    private fun size(type: Int): Int = when (type) { 5120, 5121 -> 1; 5122, 5123 -> 2; 5125, 5126 -> 4; else -> error("Unsupported glTF component type: $type") }

    private fun chunks(bytes: ByteArray): Pair<ByteArray, ByteArray?> {
        if (bytes.size < 4 || ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int != 0x46546c67) return bytes to null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.int
        require(buffer.int == 2 && buffer.int == bytes.size) { "Invalid GLB header" }
        var json: ByteArray? = null
        var binary: ByteArray? = null
        while (buffer.hasRemaining()) {
            require(buffer.remaining() >= 8) { "Truncated GLB chunk" }
            val size = buffer.int
            val type = buffer.int
            require(size >= 0 && size <= buffer.remaining()) { "Invalid GLB chunk size" }
            val chunk = ByteArray(size).also(buffer::get)
            when (type) { 0x4e4f534a -> json = chunk; 0x004e4942 -> binary = chunk }
        }
        return requireNotNull(json) { "Missing GLB JSON chunk" } to binary
    }
}
