package com.algorithmlx.ecr.common.machine

import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import kotlin.jvm.optionals.getOrNull

class ProcessingCycle {
    var progress = 0
        private set
    var result: ItemStack = ItemStack.EMPTY
        private set
    private var input: ItemStack = ItemStack.EMPTY
    private var recipeResult: ItemStack = ItemStack.EMPTY
    private var duration = 0
    private var cost = 0

    fun prepare(input: ItemStack, output: ItemStack, ticks: Int, energy: Int, multiplier: () -> Int = { 1 }): Boolean {
        if (ItemStack.isSameItemSameComponents(this.input, input) && ItemStack.matches(recipeResult, output) && duration == ticks && cost == energy) return false
        this.input = input.copyWithCount(1)
        recipeResult = output.copy()
        duration = ticks
        cost = energy
        result = output.copyWithCount(Math.multiplyExact(output.count, multiplier()))
        progress = 0
        return true
    }

    fun advance(): Boolean {
        check(duration > 0 && !result.isEmpty)
        progress++
        return progress >= duration
    }

    fun clear(): Boolean {
        if (input.isEmpty && progress == 0) return false
        input = ItemStack.EMPTY
        recipeResult = ItemStack.EMPTY
        result = ItemStack.EMPTY
        progress = 0
        duration = 0
        cost = 0
        return true
    }

    fun save(output: ValueOutput) {
        output.store("input", ItemStack.OPTIONAL_CODEC, input)
        output.store("recipe_result", ItemStack.OPTIONAL_CODEC, recipeResult)
        output.store("result", ItemStack.OPTIONAL_CODEC, result)
        output.putInt("progress", progress)
        output.putInt("duration", duration)
        output.putInt("cost", cost)
    }

    fun load(input: ValueInput) {
        this.input = input.read("input", ItemStack.OPTIONAL_CODEC).getOrNull() ?: ItemStack.EMPTY
        recipeResult = input.read("recipe_result", ItemStack.OPTIONAL_CODEC).getOrNull() ?: ItemStack.EMPTY
        result = input.read("result", ItemStack.OPTIONAL_CODEC).getOrNull() ?: ItemStack.EMPTY
        duration = input.getIntOr("duration", 0).coerceAtLeast(0)
        cost = input.getIntOr("cost", 0).coerceAtLeast(0)
        progress = input.getIntOr("progress", 0).coerceIn(0, (duration - 1).coerceAtLeast(0))
        if (this.input.isEmpty || recipeResult.isEmpty || result.isEmpty || duration == 0 || cost == 0) clear()
    }
}
