package com.algorithmlx.ecr.init.config

import com.algorithmlx.ecr.api.config.JsonDefaults
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@JsonDefaults
@Serializable
data class InventoryManagerConfig(
    @SerialName("core_capacity") val coreCapacity: Int = 5000,
    @SerialName("screen_insert_cost") val screenInsertCost: Int = 500,
    @SerialName("screen_extract_cost") val screenExtractCost: Int = 10,
    @SerialName("operation_ticks") val operationTicks: Int = 20,
    @SerialName("crafting_steps_per_tick") val craftingStepsPerTick: Int = 256,
    @SerialName("max_queued_crafts") val maxQueuedCrafts: Int = 4096,
    @SerialName("crafting_base_ticks") val craftingBaseTicks: Int = 10,
    @SerialName("crafting_ticks_per_ingredient") val craftingTicksPerIngredient: Int = 2,
    @SerialName("crafting_mru_per_complexity") val craftingMRUPerComplexity: Int = 1,
    @SerialName("dependency_max_nodes") val dependencyMaxNodes: Int = 256,
    @SerialName("dependency_max_depth") val dependencyMaxDepth: Int = 32,
    @SerialName("dependency_max_crafts") val dependencyMaxCrafts: Int = 4096,
    @SerialName("filter_max_nodes") val filterMaxNodes: Int = 256,
    @SerialName("filter_max_depth") val filterMaxDepth: Int = 8
) {
    init {
        require(coreCapacity >= 0) { "MIM capacity cannot be negative" }
        require(screenInsertCost >= 0 && screenExtractCost >= 0) { "MIM operation costs cannot be negative" }
        require(operationTicks > 0) { "MIM operation interval must be positive" }
        require(craftingStepsPerTick > 0 && maxQueuedCrafts > 0) { "MIM crafting budgets must be positive" }
        require(craftingBaseTicks > 0 && craftingTicksPerIngredient >= 0 && craftingMRUPerComplexity >= 0) { "Invalid MIM crafting costs" }
        require(dependencyMaxNodes > 0 && dependencyMaxDepth > 0 && dependencyMaxCrafts > 0) { "Invalid MIM dependency budgets" }
        require(filterMaxNodes >= 9 && filterMaxDepth >= 1) { "MIM filter budget must fit at least one nine-slot filter" }
    }
}

@JsonDefaults
@Serializable
data class MagicalMirrorConfig(
    val range: Double = 8.0,
    @SerialName("transfer_ticks") val transferTicks: Int = 60
) {
    init {
        require(range.isFinite() && range >= 0) { "Mirror range must be finite and nonnegative" }
        require(transferTicks > 0) { "Mirror transfer interval must be positive" }
    }
}
