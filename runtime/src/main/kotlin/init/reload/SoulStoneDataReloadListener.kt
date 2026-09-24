package com.algorithmlx.ecr.init.reload

import com.algorithmlx.ecr.api.LOGGER
import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.utils.rl
import com.algorithmlx.ecr.common.data.SoulStoneData
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.SimplePreparableReloadListener
import net.minecraft.util.profiling.ProfilerFiller

class SoulStoneDataReloadListener(private val json: Json): SimplePreparableReloadListener<Unit>() {
    override fun prepare(
        manager: ResourceManager,
        profiler: ProfilerFiller
    ) {}

    @OptIn(ExperimentalSerializationApi::class)
    override fun apply(
        preparations: Unit,
        manager: ResourceManager,
        profiler: ProfilerFiller
    ) {
        manager.listResources("settings/soul_stone") { it.path.endsWith(".json") && !it.path.split('/').last().startsWith('_') }.forEach {
            val data = json.decodeFromStream(SoulStoneData.serializer(), it.value.open())
            val id = data.entity.rl
            val entity = if (BuiltInRegistries.ENTITY_TYPE.containsKey(id)) BuiltInRegistries.ENTITY_TYPE.get(id) else null

            val range = data.min..data.max

            if (it.key.path.contains("default")) {
                if (it.key.path.contains("enemy")) {
                    if (it.key.namespace == ModId) SoulStoneData.defaultEnemyAdd = range
                    else if (data.entity.isEmpty()) throw IllegalStateException("Failed to set default soul stone value to null entity.")
                } else {
                    if (it.key.namespace == ModId) SoulStoneData.defaultCapacityAdd = range
                    else if (data.entity.isEmpty()) throw IllegalStateException("Failed to set default soul stone value to null entity.")
                }
                return@forEach
            }

            if (entity == null || !entity.isPresent) {
                LOGGER.warn(
                    "[Soul Stone Data] Failed to load soul stone data for entity with id \"${id}\"! " +
                            "Check the validity of the specified entity. File: ${it.key}"
                )
                return@forEach
            }

            SoulStoneData.ENTITY_CAPACITY_ADD[entity.get().value()] = range
            return@forEach
        }
    }
}