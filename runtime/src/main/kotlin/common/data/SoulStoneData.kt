package com.algorithmlx.ecr.common.data

import kotlinx.serialization.Serializable
import net.minecraft.world.entity.EntityType

@Serializable
data class SoulStoneData(
    val entity: String,
    val min: Int = defaultCapacityAdd.first,
    val max: Int = defaultCapacityAdd.last
) {
    companion object {
        @JvmField
        val ENTITY_CAPACITY_ADD = mutableMapOf<EntityType<*>, IntRange>()
        lateinit var defaultCapacityAdd: IntRange
        lateinit var defaultEnemyAdd: IntRange
    }
}
