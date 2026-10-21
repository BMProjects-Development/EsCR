package com.algorithmlx.ecr.api.geo.client

import com.algorithmlx.ecr.api.LOGGER
import com.algorithmlx.ecr.api.geo.GeoModel
import com.algorithmlx.ecr.api.geo.file.BedrockAnimation
import com.algorithmlx.ecr.api.geo.file.BedrockGeoFileParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener

object BedrockGeoAssets: ResourceManagerReloadListener {
    private const val GEOMETRY_SUFFIX = ".geo.json"

    private val GEOMETRY_FOLDERS = listOf("geo", "models")
    private val ANIMATION_FOLDERS = listOf("geo", "animations")

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Volatile
    private var geometries: Map<String, BakedGeoModel> = emptyMap()

    @Volatile
    private var geometryResources: Map<Identifier, List<BakedGeoModel>> = emptyMap()

    @Volatile
    private var animations: Map<String, BedrockAnimation> = emptyMap()

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        val loadedGeometries = linkedMapOf<String, BakedGeoModel>()
        val loadedGeometryResources = linkedMapOf<Identifier, MutableList<BakedGeoModel>>()
        val loadedAnimations = linkedMapOf<String, BedrockAnimation>()

        findResources(resourceManager, GEOMETRY_FOLDERS, ".geo.json").forEach { (location, resource) ->
            runCatching {
                resource.openAsReader().use { reader ->
                    BedrockGeoFileParser.parseGeometry(json.parseToJsonElement(reader.readText()).jsonObject)
                }
            }.onSuccess { parsed ->
                val baked = parsed.map(BedrockGeoBaker::bake)
                baked.forEach { geometry ->
                    loadedGeometries[geometry.identifier] = geometry
                }
                geometryResourceAliases(location).forEach { alias ->
                    loadedGeometryResources.getOrPut(alias, ::arrayListOf).addAll(baked)
                }
            }.onFailure { error ->
                LOGGER.error("Unable to load Bedrock GEO model {}", location, error)
            }
        }

        findResources(resourceManager, ANIMATION_FOLDERS, ".animation.json").forEach { (location, resource) ->
            runCatching {
                resource.openAsReader().use { reader ->
                    BedrockGeoFileParser.parseAnimations(json.parseToJsonElement(reader.readText()).jsonObject)
                }
            }.onSuccess(loadedAnimations::putAll)
                .onFailure { error -> LOGGER.error("Unable to load Bedrock GEO animations {}", location, error) }
        }

        geometries = loadedGeometries.toMap()
        geometryResources = loadedGeometryResources.mapValues { (_, models) -> models.toList() }
        animations = loadedAnimations.toMap()
        ClientGeoAnimations.onAssetsReload()
        LOGGER.info(
            "Loaded {} Bedrock GEO geometries and {} animations",
            loadedGeometries.size,
            loadedAnimations.size
        )
    }

    operator fun get(identifier: String): BakedGeoModel? = geometries[identifier]

    operator fun get(resource: Identifier): BakedGeoModel? = geometryResources[resource]?.singleOrNull()

    operator fun get(model: GeoModel): BakedGeoModel? =
        model.geometryResource?.let { resource -> this[resource] } ?: this[model.geometry]

    fun geometryCount(resource: Identifier): Int = geometryResources[resource]?.size ?: 0

    fun animation(identifier: String): BedrockAnimation? = animations[identifier]

    fun hasAnimation(identifier: String): Boolean = identifier in animations

    internal fun geometryResourceAliases(location: Identifier): Set<Identifier> = buildSet {
        add(location)
        val folder = GEOMETRY_FOLDERS.firstOrNull { candidate ->
            location.path.startsWith("$candidate/")
        } ?: return@buildSet
        val relativePath = location.path
            .removePrefix("$folder/")
            .removeSuffix(GEOMETRY_SUFFIX)
        if (relativePath != location.path && relativePath.isNotEmpty())
            add(Identifier.fromNamespaceAndPath(location.namespace, relativePath))
    }

    private fun findResources(
        manager: ResourceManager,
        folders: List<String>,
        suffix: String
    ) = buildMap {
        folders.forEach { folder ->
            putAll(manager.listResources(folder) { location -> location.path.endsWith(suffix) })
        }
    }
}
