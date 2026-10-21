package com.algorithmlx.ecr.init.reload

import com.algorithmlx.ecr.api.research.ResearchCatalog
import com.algorithmlx.ecr.api.research.ResearchJson
import com.algorithmlx.ecr.api.research.serializer.researchJson
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import net.minecraft.resources.FileToIdConverter
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener

class ResearchReloadListener(
    private val afterReload: () -> Unit = {}
) : ResourceManagerReloadListener {
    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        val categories = read(resourceManager, FileToIdConverter.json("research/categories"), ResearchJson::decodeCategory)
        val icons = readMap(resourceManager, FileToIdConverter.json("research/icons"), ResearchJson::decodeTaskIcons)
        val entries = read(resourceManager, FileToIdConverter.json("research/entries"), ResearchJson::decodeEntry)
            .map { entry -> entry.copy(taskIcons = entry.taskIcons + icons[entry.id].orEmpty()) }
        ResearchCatalog.replace(categories, entries)
        afterReload()
    }

    private fun <T> read(
        resourceManager: ResourceManager,
        converter: FileToIdConverter,
        decoder: (Identifier, JsonObject) -> T
    ): List<T> = converter.listMatchingResources(resourceManager).map { (file, resource) ->
        resource.openAsReader().use { decoder(converter.fileToId(file), researchJson.parseToJsonElement(it.readText()).jsonObject) }
    }

    private fun <T> readMap(
        resourceManager: ResourceManager,
        converter: FileToIdConverter,
        decoder: (JsonObject) -> T
    ): Map<Identifier, T> = converter.listMatchingResources(resourceManager).mapValues { (_, resource) ->
        resource.openAsReader().use { decoder(researchJson.parseToJsonElement(it.readText()).jsonObject) }
    }.mapKeys { converter.fileToId(it.key) }
}