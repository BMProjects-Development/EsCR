package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.mim.MIMCraftingWork
import com.algorithmlx.ecr.api.mru.storage.ModifiableMRUStorage
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.item.ItemStack

internal class MIMCraftingProgress(
    inputs: List<ItemStack>, outputs: List<ItemStack>, val work: MIMCraftingWork,
    var completed: Int = 0, var partialMRU: Int = 0
) {
    private val inputs = inputs.map(ItemStack::copy)
    private val outputs = outputs.map(ItemStack::copy)
    private var lastTick = Long.MIN_VALUE
    val complete: Boolean get() = completed >= work.ticks

    init {
        require(completed in 0..work.ticks && partialMRU >= 0 && (partialMRU == 0 || partialMRU < work.mruPerTick))
    }

    fun matches(inputs: List<ItemStack>, outputs: List<ItemStack>, work: MIMCraftingWork): Boolean =
        this.work == work && same(this.inputs, inputs) && same(this.outputs, outputs)

    fun advance(storage: ModifiableMRUStorage, tick: Long): Boolean {
        if (complete || tick == lastTick) return false
        lastTick = tick
        val remaining = work.mruPerTick - partialMRU
        if (remaining > 0) {
            val extracted = storage.extract(minOf(remaining, storage.mru))
            if (extracted == 0) return false
            partialMRU += extracted
        }
        if (partialMRU == work.mruPerTick) {
            completed++
            partialMRU = 0
        }
        return true
    }

    private fun same(first: List<ItemStack>, second: List<ItemStack>): Boolean =
        first.size == second.size && first.indices.all { ItemStack.matches(first[it], second[it]) }

    companion object {
        val CODEC: Codec<MIMCraftingProgress> = RecordCodecBuilder.create { instance ->
            instance.group(
                ItemStack.CODEC.listOf().fieldOf("inputs").forGetter(MIMCraftingProgress::inputs),
                ItemStack.CODEC.listOf().fieldOf("outputs").forGetter(MIMCraftingProgress::outputs),
                Codec.intRange(1, Int.MAX_VALUE).fieldOf("ticks").forGetter { it.work.ticks },
                Codec.intRange(0, Int.MAX_VALUE).fieldOf("mru_per_tick").forGetter { it.work.mruPerTick },
                Codec.intRange(0, Int.MAX_VALUE).fieldOf("completed").forGetter(MIMCraftingProgress::completed),
                Codec.intRange(0, Int.MAX_VALUE).fieldOf("partial_mru").forGetter(MIMCraftingProgress::partialMRU)
            ).apply(instance) { inputs, outputs, ticks, rate, completed, partial ->
                MIMCraftingProgress(inputs, outputs, MIMCraftingWork(ticks, rate), completed, partial)
            }
        }
    }
}
