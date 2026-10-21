package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.mim.MIMCraftingPattern
import com.algorithmlx.ecr.api.mim.MIMCraftingWork
import com.algorithmlx.ecr.init.config.ECConfig

object MIMCraftingCosts {
    fun work(pattern: MIMCraftingPattern): MIMCraftingWork {
        pattern.work?.let { return it }
        val config = ECConfig.current.inventoryManager
        val complexity = pattern.complexity.toLong()
        return MIMCraftingWork(
            (config.craftingBaseTicks + complexity * config.craftingTicksPerIngredient).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            (complexity * config.craftingMRUPerComplexity).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )
    }
}
