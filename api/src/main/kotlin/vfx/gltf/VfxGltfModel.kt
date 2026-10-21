package com.algorithmlx.ecr.api.vfx.gltf

import com.algorithmlx.ecr.api.vfx.array
import com.algorithmlx.ecr.api.vfx.bool
import com.algorithmlx.ecr.api.vfx.float
import com.algorithmlx.ecr.api.vfx.number
import com.algorithmlx.ecr.api.vfx.obj
import com.algorithmlx.ecr.api.vfx.string
import com.algorithmlx.ecr.api.vfx.vector
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import net.minecraft.resources.Identifier
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f

internal data class VfxGltfVertex(val position: Vector3f, val normal: Vector3f, val uv: Vector2f, val color: FloatArray)
internal data class VfxGltfImage(val id: Identifier, val bytes: ByteArray?)
internal class VfxGltfMesh(val vertices: List<VfxGltfVertex>, val texture: VfxGltfImage, val material: JsonObject)
internal data class VfxGltfModel(val meshes: List<VfxGltfMesh>)

internal class VfxGltfLoader(private val document: VfxGltfDocument) {
    private val root = document.root
    private val nodes = root.array("nodes").map { it.obj() }
    private val parents = IntArray(nodes.size) { -1 }.also { parent -> nodes.forEachIndexed { index, node ->
        node.array("children").forEach { raw ->
            val child = raw.float().toInt()
            require(child in nodes.indices && parent[child] == -1) { "Invalid glTF node hierarchy" }
            parent[child] = index
        }
    } }
    private val matrices = arrayOfNulls<Matrix4f>(nodes.size)
    private val visiting = hashSetOf<Int>()
    private val images = root.array("images").mapIndexed { index, item ->
        val image = item.obj()
        val uri = image.string("uri")
        val bytes = when {
            uri.startsWith("data:") -> document.bytes(uri)
            "bufferView" in image -> document.view(image.number("bufferView").toInt())
            else -> null
        }
        val id = if (bytes == null) document.resolve(uri) else Identifier.fromNamespaceAndPath("escr", "dynamic/vfx/gltf/${document.id.namespace}/${document.id.path}/image_$index")
        VfxGltfImage(id, bytes)
    }

    fun load(): VfxGltfModel {
        val scene = root.array("scenes").getOrNull(root.number("scene").toInt())?.obj()
        val roots = scene?.array("nodes")?.map { it.float().toInt() } ?: nodes.indices.filter { parents[it] < 0 }
        val active = linkedSetOf<Int>()
        fun visit(index: Int) {
            require(index in nodes.indices) { "Invalid glTF scene node" }
            if (!active.add(index)) return
            nodes[index].array("children").forEach { visit(it.float().toInt()) }
        }
        roots.forEach(::visit)
        val meshes = active.flatMap { index ->
            val node = nodes[index]
            if ("mesh" !in node)
                emptyList()
            else
                root.array("meshes")[node.number("mesh").toInt()].obj().array("primitives").map { primitive ->
                    mesh(index, primitive.obj())
                }
        }
        require(meshes.isNotEmpty()) { "glTF scene contains no triangle meshes" }
        return VfxGltfModel(meshes)
    }

    private fun matrix(index: Int): Matrix4f {
        matrices[index]?.let { return it }
        require(visiting.add(index)) { "Cyclic glTF hierarchy" }
        val node = nodes[index]
        val local = if ("matrix" in node)
            Matrix4f().set(node.array("matrix").map { it.float() }.toFloatArray())
        else {
            val rotation = node.array("rotation")
            Matrix4f().translationRotateScale(node.vector("translation"), Quaternionf(
                rotation.getOrNull(0).float(), rotation.getOrNull(1).float(), rotation.getOrNull(2).float(), rotation.getOrNull(3).float(1f)
            ), node.vector("scale", 1f))
        }
        val result = if (parents[index] < 0) local else Matrix4f(matrix(parents[index])).mul(local)
        visiting.remove(index)
        matrices[index] = result
        return result
    }

    private fun mesh(nodeIndex: Int, primitive: JsonObject): VfxGltfMesh {
        val attributes = primitive.obj("attributes")
        val positionIndex = requireNotNull(attributes["POSITION"]) { "glTF mesh requires positions" }.float().toInt()
        val positions = document.accessor(positionIndex)
        require(document.dimensions(positionIndex) == 3) { "glTF positions must be VEC3" }
        val count = positions.size / 3
        fun attribute(name: String) = attributes[name]?.let { document.accessor(it.float().toInt()) }
        val normals = attribute("NORMAL")
        val colors = attribute("COLOR_0")
        val colorSize = attributes["COLOR_0"]?.let { document.dimensions(it.float().toInt()) } ?: 4
        val joints = attribute("JOINTS_0")
        val weights = attribute("WEIGHTS_0")
        val skin = nodes[nodeIndex]["skin"]?.let { root.array("skins")[it.float().toInt()].obj() }
        val jointNodes = skin?.array("joints")?.map { it.float().toInt() }.orEmpty()
        val binds = skin?.get("inverseBindMatrices")?.let { document.accessor(it.float().toInt()) }
        val skinMatrices = jointNodes.mapIndexed { index, joint ->
            Matrix4f(matrix(joint)).mul(if (binds == null) Matrix4f() else Matrix4f().set(binds.copyOfRange(index * 16, (index + 1) * 16)))
        }
        val transform = matrix(nodeIndex)
        val normalTransform = Matrix3f(transform).invert().transpose()
        val meshSpec = root.array("meshes")[nodes[nodeIndex].number("mesh").toInt()].obj()
        val morphWeights = (nodes[nodeIndex]["weights"] ?: meshSpec["weights"])
        val weightsSpec = morphWeights as? JsonArray
        val morphs = primitive.array("targets").map { target ->
            val fields = target.obj()
            fields["POSITION"]?.let { document.accessor(it.float().toInt()) } to fields["NORMAL"]?.let { document.accessor(it.float().toInt()) }
        }
        val material = primitive["material"]?.let { root.array("materials")[it.float().toInt()].obj() } ?: JsonObject(emptyMap())
        val pbr = material.obj("pbrMetallicRoughness")
        val textureInfo = pbr.obj("baseColorTexture")
        val textureTransform = textureInfo.obj("extensions").obj("KHR_texture_transform")
        val texCoord = textureTransform.number("texCoord", textureInfo.number("texCoord")).toInt()
        val uvs = attribute("TEXCOORD_$texCoord")
        val factor = pbr.array("baseColorFactor")
        val vertices = List(count) { index ->
            val position = Vector3f(positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2])
            val normal = if (normals == null) Vector3f() else Vector3f(normals[index * 3], normals[index * 3 + 1], normals[index * 3 + 2])
            morphs.forEachIndexed { morphIndex, (positionDelta, normalDelta) ->
                val weight = weightsSpec?.getOrNull(morphIndex).float()
                if (positionDelta != null) position.add(positionDelta[index * 3] * weight, positionDelta[index * 3 + 1] * weight, positionDelta[index * 3 + 2] * weight)
                if (normalDelta != null) normal.add(normalDelta[index * 3] * weight, normalDelta[index * 3 + 1] * weight, normalDelta[index * 3 + 2] * weight)
            }
            if (skinMatrices.isNotEmpty() && joints != null && weights != null) {
                val point = Vector3f()
                val direction = Vector3f()
                var total = 0f
                repeat(4) { component ->
                    val weight = weights[index * 4 + component]
                    if (weight > 0f) {
                        val joint = skinMatrices[joints[index * 4 + component].toInt()]
                        point.fma(weight, joint.transformPosition(Vector3f(position)))
                        direction.fma(weight, Matrix3f(joint).invert().transpose().transform(Vector3f(normal)))
                        total += weight
                    }
                }
                if (total > 0f) { position.set(point).div(total); normal.set(direction) }
                else { transform.transformPosition(position); normalTransform.transform(normal) }
            } else { transform.transformPosition(position); normalTransform.transform(normal) }
            if (normal.lengthSquared() > 0f) normal.normalize()
            val uv = Vector2f(uvs?.get(index * 2) ?: 0f, uvs?.get(index * 2 + 1) ?: 0f)
            val scale = textureTransform.array("scale")
            uv.mul(scale.getOrNull(0).float(1f), scale.getOrNull(1).float(1f))
            val angle = textureTransform.number("rotation")
            uv.rotate(angle)
            val offset = textureTransform.array("offset")
            uv.add(offset.getOrNull(0).float(), offset.getOrNull(1).float())
            VfxGltfVertex(position, normal, uv, FloatArray(4) { channel ->
                factor.getOrNull(channel).float(1f) * if (colors == null || channel >= colorSize) 1f else colors[index * colorSize + channel]
            })
        }
        val indices = primitive["indices"]?.let { document.accessor(it.float().toInt()).map(Float::toInt) } ?: (0 until count).toList()
        val triangles = when (primitive.number("mode", 4f).toInt()) {
            4 -> { require(indices.size % 3 == 0); indices.chunked(3) }
            5 -> (0 until indices.size - 2).map { i -> if (i % 2 == 0) listOf(indices[i], indices[i + 1], indices[i + 2]) else listOf(indices[i + 1], indices[i], indices[i + 2]) }
            6 -> (1 until indices.size - 1).map { i -> listOf(indices[0], indices[i], indices[i + 1]) }
            else -> error("VFX glTF renderer requires triangle primitives")
        }.flatMap { triangle ->
            val selected = triangle.map { require(it in vertices.indices) { "Invalid glTF index" }; vertices[it] }
            if (normals != null)
                selected
            else {
                val normal = Vector3f(selected[1].position).sub(selected[0].position).cross(Vector3f(selected[2].position).sub(selected[0].position))
                if (normal.lengthSquared() > 0f) normal.normalize()
                selected.map { it.copy(normal = Vector3f(normal)) }
            }
        }
        val image = textureInfo["index"]?.let { raw ->
            val texture = root.array("textures")[raw.float().toInt()].obj()
            images[texture.number("source").toInt()]
        } ?: VfxGltfImage(Identifier.parse("hollowengine:textures/particle/white.png"), null)
        val style = JsonObject(mapOf(
            "blend" to JsonPrimitive(when (material.string("alphaMode", "OPAQUE")) { "BLEND" -> "BLEND"; "MASK" -> "CUTOUT"; else -> "OPAQUE" }),
            "cull" to JsonPrimitive(!material.bool("doubleSided")),
            "depthWrite" to JsonPrimitive(material.string("alphaMode") != "BLEND"),
            "alphaCutoff" to JsonPrimitive(material.number("alphaCutoff", 0.5f)),
            "lighting" to JsonPrimitive(if ("KHR_materials_unlit" in material.obj("extensions")) "UNLIT" else "WORLD")
        ))
        return VfxGltfMesh(triangles, image, style)
    }
}
