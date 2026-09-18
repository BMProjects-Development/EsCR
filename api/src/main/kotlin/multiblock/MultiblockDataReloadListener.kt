package com.algorithmlx.ecr.api.multiblock

import com.algorithmlx.ecr.api.LOGGER
import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledBlockMatcher
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDefinition
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockPart
import com.algorithmlx.ecr.api.geo.GeoBlockRotation
import com.algorithmlx.ecr.api.geo.GeoLightMode
import com.algorithmlx.ecr.api.geo.GeoModel
import com.algorithmlx.ecr.api.geo.GeoRenderType
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.FileToIdConverter
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.tags.TagKey
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.shapes.Shapes

class MultiblockDataReloadListener : SimplePreparableReloadListener<MultiblockDataReloadListener.Prepared>() {
    override fun prepare(resourceManager: ResourceManager, profiler: ProfilerFiller): Prepared {
        val allowed = ECRegistries.MULTIBLOCK.keySet()
        val allowedAssembled = ECRegistries.ASSEMBLED_MULTIBLOCK.keySet()
        val required = allowed.filterTo(linkedSetOf()) { id ->
            ECRegistries.MULTIBLOCK.getValue(id)?.requiresJsonDefinition == true
        }
        val requiredAssembled = allowedAssembled.filterTo(linkedSetOf()) { id ->
            ECRegistries.ASSEMBLED_MULTIBLOCK.getValue(id)?.requiresJsonDefinition == true
        }

        val multiblockJson = readJson(resourceManager, MULTIBLOCK_CONVERTER)
        val assembledJson = readJson(resourceManager, ASSEMBLED_CONVERTER)
        rejectUnregistered(multiblockJson.keys, allowed, "multiblock")
        rejectUnregistered(assembledJson.keys, allowedAssembled, "assembled multiblock")
        requirePresent(required, multiblockJson.keys, MULTIBLOCK_DIRECTORY)
        requirePresent(requiredAssembled, assembledJson.keys, ASSEMBLED_DIRECTORY)

        val multiblocks = multiblockJson.mapValues { (id, json) ->
            decodeWithContext(id, MULTIBLOCK_DIRECTORY) { decodeMultiblock(json) }
        }
        val assembled = assembledJson.mapValues { (id, json) ->
            decodeWithContext(id, ASSEMBLED_DIRECTORY) {
                decodeAssembledMultiblock(
                    id,
                    json,
                    ECRegistries.ASSEMBLED_MULTIBLOCK.getOptional(id).orElse(null)
                )
            }
        }
        return Prepared(
            multiblocks,
            assembled,
            MultiblockJsonResources(
                multiblockJson.mapValues { (_, json) -> json.toString() },
                assembledJson.mapValues { (_, json) -> json.toString() }
            )
        )
    }

    override fun apply(preparations: Prepared, resourceManager: ResourceManager, profiler: ProfilerFiller) {
        MultiblockDefinitions.installJsonDefinitions(
            preparations.multiblocks,
            preparations.assembledMultiblocks,
            preparations.resources
        )
        LOGGER.info(
            "Loaded {} JSON multiblocks and {} JSON assembled multiblocks",
            preparations.multiblocks.size,
            preparations.assembledMultiblocks.size
        )
    }

    internal fun decodeMultiblock(json: JsonObject): Multiblock {
        val layout = decodePattern(json.requiredArray("pattern"))
        val keys = decodeKeys(json.requiredObject("keys"))
        val blocks = layout.symbols.map { symbol ->
            if (symbol == EMPTY_SYMBOL && symbol !in keys) optionalAir() else keys[symbol]
                ?: error("Pattern uses symbol '$symbol' which is absent from keys")
        }.toTypedArray()
        return Multiblock(layout.xSize, layout.zSize, layout.ySize) {
            pattern(*blocks)
        }
    }

    internal fun decodeAssembledMultiblock(
        id: Identifier,
        json: JsonObject,
        fallback: AssembledMultiblockDefinition?
    ): AssembledMultiblockDefinition {
        val effectiveFallback = fallback?.takeUnless { it.requiresJsonDefinition }
        val layout = decodePattern(json.requiredArray("pattern"))
        val keys = decodeKeys(json.requiredObject("keys"))
        val controller = decodeCoordinate(json.get("controller"), "controller", layout)
        val controllerIndex = layout.index(controller)
        val controllerSymbol = layout.symbols[controllerIndex]
        require(controllerSymbol != EMPTY_SYMBOL || controllerSymbol in keys) {
            "Assembled multiblock controller points to an empty pattern cell"
        }
        require(keys[controllerSymbol]?.required == true) {
            "Assembled multiblock controller must use a required matcher"
        }

        val fallbackParts = effectiveFallback?.parts?.associateBy(AssembledMultiblockPart::offset).orEmpty()
        val parts = layout.symbols.mapIndexedNotNull { index, symbol ->
            if (symbol == EMPTY_SYMBOL && symbol !in keys) return@mapIndexedNotNull null
            val matcher = keys[symbol]
                ?: error("Pattern uses symbol '$symbol' which is absent from keys")
            if (!matcher.required) return@mapIndexedNotNull null
            val position = layout.position(index)
            val offset = position.subtract(controller)
            AssembledMultiblockPart(
                offset,
                matcher.asAssembledMatcher(),
                fallbackParts[offset]?.formedShape ?: Shapes.block()
            )
        }
        val anchor = json.get("model_anchor")?.let {
            decodeCoordinate(it, "model_anchor", layout).subtract(controller)
        } ?: effectiveFallback?.formedModelAnchor ?: BlockPos.ZERO
        val shapeOrigin = json.get("formed_shape_origin")?.let {
            decodeCoordinate(it, "formed_shape_origin", layout).subtract(controller)
        } ?: effectiveFallback?.formedShapeOrigin ?: anchor
        val formedModel = when {
            !json.has("formed_model") -> effectiveFallback?.formedModel
            json.get("formed_model") is JsonNull -> null
            else -> decodeGeoModel(json.getAsJsonObject("formed_model"))
        }
        val allowAnyPart = json.optionalBoolean(
            "allow_assembly_from_any_part",
            effectiveFallback?.allowAssemblyFromAnyPart ?: false
        )

        return AssembledMultiblockDefinition(
            id,
            parts,
            formedModel,
            allowAnyPart,
            anchor,
            effectiveFallback?.formedStructureShape,
            shapeOrigin
        )
    }

    private fun decodePattern(layers: JsonArray): PatternLayout {
        require(layers.size() > 0) { "pattern must contain at least one layer" }
        val decodedLayers = layers.mapIndexed { y, layerElement ->
            val rows = layerElement as? JsonArray
                ?: error("pattern layer $y must be an array of row strings")
            require(rows.size() > 0) { "pattern layer $y must contain at least one row" }
            rows.mapIndexed { z, rowElement ->
                require(rowElement.isJsonPrimitive && rowElement.asJsonPrimitive.isString) {
                    "pattern row [$y][$z] must be a string"
                }
                rowElement.asString
            }
        }
        val zSize = decodedLayers.first().size
        val xSize = decodedLayers.first().first().length
        require(xSize > 0) { "pattern rows must not be empty" }
        decodedLayers.forEachIndexed { y, rows ->
            require(rows.size == zSize) {
                "pattern layer $y has ${rows.size} rows; expected $zSize"
            }
            rows.forEachIndexed { z, row ->
                require(row.length == xSize) {
                    "pattern row [$y][$z] has width ${row.length}; expected $xSize"
                }
            }
        }
        return PatternLayout(
            xSize,
            zSize,
            decodedLayers.size,
            decodedLayers.flatMap { rows -> rows.flatMap(String::toList) }
        )
    }

    private fun decodeKeys(json: JsonObject): Map<Char, MultiblockMatcher> = buildMap {
        json.entrySet().forEach { (rawSymbol, matcherJson) ->
            require(rawSymbol.length == 1) { "Multiblock key '$rawSymbol' must be exactly one character" }
            val symbol = rawSymbol[0]
            check(put(symbol, decodeMatcher(matcherJson)) == null) {
                "Duplicate multiblock key '$symbol'"
            }
        }
    }

    private fun decodeMatcher(json: JsonElement): MultiblockMatcher {
        if (json is JsonNull) return optionalAir()
        if (json.isJsonPrimitive && json.asJsonPrimitive.isString) {
            val value = json.asString
            if (value.startsWith('#')) {
                val id = parseId(value.drop(1), "block tag")
                return TagMultiblockMatcher(TagKey.create(Registries.BLOCK, id))
            }
            val id = parseId(value, "block")
            val block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null)
                ?: error("Unknown block '$id'")
            return BlockMultiblockMatcher(block.defaultBlockState(), ignoreTag = true)
        }

        val normalized = json.deepCopy()
        normalizeMatcherTypeIds(normalized)
        return MultiblockMatcher.CODEC.parse(JsonOps.INSTANCE, normalized)
            .getOrThrow { message -> IllegalArgumentException(message) }
    }

    private fun decodeGeoModel(json: JsonObject): GeoModel {
        val texture = parseId(json.requiredString("texture"), "GEO texture")
        val renderType = json.optionalString("render_type")
            ?.let { value -> enumValue<GeoRenderType>(value, "render_type") }
            ?: GeoRenderType.CUTOUT
        val lightMode = json.optionalString("light_mode")
            ?.let { value -> enumValue<GeoLightMode>(value, "light_mode") }
            ?: GeoLightMode.WORLD
        val scale = json.optionalFloat("scale", 1F)
        val shadowRadius = json.optionalFloat("shadow_radius", 0F)
        val rotation = json.getAsJsonObject("block_rotation")?.let { rotationJson ->
            GeoBlockRotation(
                rotationJson.optionalBoolean("enabled", true),
                rotationJson.optionalBoolean("opposite", false)
            )
        } ?: GeoBlockRotation.NONE
        val resource = json.optionalString("geometry_resource")
        val geometry = json.optionalString("geometry")
        require((resource == null) != (geometry == null)) {
            "formed_model must contain exactly one of geometry or geometry_resource"
        }
        return if (resource != null) {
            GeoModel(
                parseId(resource, "GEO geometry resource"),
                texture,
                renderType,
                scale,
                shadowRadius,
                lightMode,
                rotation
            )
        } else {
            GeoModel(
                requireNotNull(geometry),
                texture,
                renderType,
                scale,
                shadowRadius,
                null,
                lightMode,
                rotation
            )
        }
    }

    private fun decodeCoordinate(element: JsonElement?, name: String, layout: PatternLayout): BlockPos {
        val coordinates = element as? JsonArray ?: error("$name must be an [x, y, z] array")
        require(coordinates.size() == 3) { "$name must contain exactly three integers: [x, y, z]" }
        val values = coordinates.mapIndexed { index, value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) {
                "$name[$index] must be an integer"
            }
            val number = value.asNumber
            val integer = number.toInt()
            require(number.toDouble() == integer.toDouble()) { "$name[$index] must be an integer" }
            integer
        }
        val position = BlockPos(values[0], values[1], values[2])
        require(layout.contains(position)) {
            "$name $position is outside pattern bounds [${layout.xSize}, ${layout.ySize}, ${layout.zSize}]"
        }
        return position
    }

    private fun readJson(
        resourceManager: ResourceManager,
        converter: FileToIdConverter
    ): Map<Identifier, JsonObject> = converter.listMatchingResources(resourceManager)
        .entries
        .sortedBy { (file, _) -> file.toString() }
        .associate { (file, resource) ->
            val id = converter.fileToId(file)
            val json = resource.openAsReader().use { reader -> JsonParser.parseReader(reader) }
            id to (json as? JsonObject ?: error("Data resource $file must contain a JSON object"))
        }

    private fun rejectUnregistered(found: Set<Identifier>, allowed: Set<Identifier>, kind: String) {
        val unknown = found - allowed
        require(unknown.isEmpty()) {
            "JSON $kind definition(s) are not registered: ${unknown.sortedBy(Identifier::toString).joinToString()}"
        }
    }

    private fun requirePresent(required: Set<Identifier>, found: Set<Identifier>, directory: String) {
        val missing = required - found
        require(missing.isEmpty()) {
            "Missing required JSON definition(s) in data/*/$directory: " +
                missing.sortedBy(Identifier::toString).joinToString()
        }
    }

    private fun parseId(value: String, description: String): Identifier =
        Identifier.tryParse(value) ?: error("Invalid $description identifier '$value'")

    private inline fun <reified T : Enum<T>> enumValue(value: String, name: String): T =
        enumValues<T>().firstOrNull { it.name.equals(value, ignoreCase = true) }
            ?: error("Unknown $name '$value'; expected ${enumValues<T>().joinToString { it.name.lowercase() }}")

    private inline fun <T> decodeWithContext(id: Identifier, directory: String, decoder: () -> T): T =
        try {
            decoder()
        } catch (error: Exception) {
            throw IllegalArgumentException(
                "Failed to decode data/${id.namespace}/$directory/${id.path}.json: ${error.message}",
                error
            )
        }

    data class Prepared(
        val multiblocks: Map<Identifier, Multiblock>,
        val assembledMultiblocks: Map<Identifier, AssembledMultiblockDefinition>,
        val resources: MultiblockJsonResources
    )

    private data class PatternLayout(
        val xSize: Int,
        val zSize: Int,
        val ySize: Int,
        val symbols: List<Char>
    ) {
        fun index(position: BlockPos): Int =
            position.x + position.z * xSize + position.y * xSize * zSize

        fun position(index: Int): BlockPos {
            val layerSize = xSize * zSize
            val y = index / layerSize
            val layerIndex = index % layerSize
            return BlockPos(layerIndex % xSize, y, layerIndex / xSize)
        }

        fun contains(position: BlockPos): Boolean =
            position.x in 0..<xSize && position.y in 0..<ySize && position.z in 0..<zSize
    }

    companion object {
        const val MULTIBLOCK_DIRECTORY = "multiblocks"
        const val ASSEMBLED_DIRECTORY = "assembled_multiblocks"

        private val MULTIBLOCK_CONVERTER = FileToIdConverter.json(MULTIBLOCK_DIRECTORY)
        private val ASSEMBLED_CONVERTER = FileToIdConverter.json(ASSEMBLED_DIRECTORY)
        private const val EMPTY_SYMBOL = ' '

        private fun optionalAir() = BlockMultiblockMatcher(
            Blocks.AIR.defaultBlockState(),
            required = false
        )

        private fun MultiblockMatcher.asAssembledMatcher(): AssembledBlockMatcher =
            object : AssembledBlockMatcher {
                override fun matches(state: net.minecraft.world.level.block.state.BlockState): Boolean =
                    this@asAssembledMatcher.matches(state)

                override fun previewState(): net.minecraft.world.level.block.state.BlockState =
                    this@asAssembledMatcher.default()
            }

        private fun JsonObject.requiredArray(name: String): JsonArray = get(name) as? JsonArray
            ?: error("$name must be an array")

        private fun JsonObject.requiredObject(name: String): JsonObject = get(name) as? JsonObject
            ?: error("$name must be an object")

        private fun JsonObject.requiredString(name: String): String = optionalString(name)
            ?: error("$name must be a string")

        private fun JsonObject.optionalString(name: String): String? = get(name)?.let { value ->
            require(value.isJsonPrimitive && value.asJsonPrimitive.isString) { "$name must be a string" }
            value.asString
        }

        private fun JsonObject.optionalBoolean(name: String, default: Boolean): Boolean =
            get(name)?.let { value ->
                require(value.isJsonPrimitive && value.asJsonPrimitive.isBoolean) { "$name must be a boolean" }
                value.asBoolean
            } ?: default

        private fun JsonObject.optionalFloat(name: String, default: Float): Float =
            get(name)?.let { value ->
                require(value.isJsonPrimitive && value.asJsonPrimitive.isNumber) { "$name must be a number" }
                value.asFloat
            } ?: default

        private fun normalizeMatcherTypeIds(element: JsonElement) {
            when (element) {
                is JsonObject -> {
                    element.get("type")
                        ?.takeIf { child -> child.isJsonPrimitive && child.asJsonPrimitive.isString }
                        ?.asString
                        ?.takeIf { value -> ':' !in value }
                        ?.let { value -> element.addProperty("type", "$ModId:$value") }
                    element.get("matchers")?.let(::normalizeMatcherTypeIds)
                }
                is JsonArray -> element.forEach(::normalizeMatcherTypeIds)
            }
        }
    }
}

object MultiblockJsonSync {
    @JvmStatic
    fun apply(resources: MultiblockJsonResources) {
        val decoder = MultiblockDataReloadListener()
        val multiblocks = resources.multiblocks.mapValues { (id, source) ->
            decode(id, MultiblockDataReloadListener.MULTIBLOCK_DIRECTORY, source) { json ->
                decoder.decodeMultiblock(json)
            }
        }
        val assembled = resources.assembledMultiblocks.mapValues { (id, source) ->
            decode(id, MultiblockDataReloadListener.ASSEMBLED_DIRECTORY, source) { json ->
                decoder.decodeAssembledMultiblock(
                    id,
                    json,
                    ECRegistries.ASSEMBLED_MULTIBLOCK.getOptional(id).orElse(null)
                )
            }
        }
        MultiblockDefinitions.installJsonDefinitions(multiblocks, assembled, resources)
    }

    private inline fun <T> decode(
        id: Identifier,
        directory: String,
        source: String,
        decoder: (JsonObject) -> T
    ): T = try {
        val element = JsonParser.parseString(source)
        decoder(element as? JsonObject ?: error("Definition root must be an object"))
    } catch (error: Exception) {
        throw IllegalArgumentException(
            "Failed to decode synchronized data/${id.namespace}/$directory/${id.path}.json: ${error.message}",
            error
        )
    }
}
