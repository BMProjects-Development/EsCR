package com.algorithmlx.ecr.api.mim

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack

fun interface MIMFrameProvider {
    fun patterns(level: ServerLevel, frame: ItemStack): List<MIMCraftingPattern>
}

class MIMCraftingPattern(
    val key: Any,
    output: ItemStack,
    ingredients: List<ItemStack>,
    val accepts: (slot: Int, candidate: ItemStack) -> Boolean,
    val outputs: (ingredients: List<ItemStack>) -> List<ItemStack>?,
    val isolatedOutputs: Set<Int> = emptySet(),
    val prepareOutputs: ((List<ItemStack>) -> List<ItemStack>)? = null,
    val complexity: Int = ingredients.sumOf { it.count.toLong() }.coerceIn(1, Int.MAX_VALUE.toLong()).toInt(),
    val work: MIMCraftingWork? = null,
    byproducts: List<ItemStack> = emptyList()
) {
    private val result = output.copy()
    private val inputs = ingredients.map(ItemStack::copy)
    private val remainders = byproducts.filterNot(ItemStack::isEmpty).map(ItemStack::copy)
    val output: ItemStack get() = result.copy()
    val ingredients: List<ItemStack> get() = inputs.map(ItemStack::copy)
    val guaranteedOutputs: List<ItemStack> get() = listOf(result.copy()) + remainders.map(ItemStack::copy)

    init {
        require(!result.isEmpty) { "A MIM crafting pattern must produce an item" }
        require(complexity > 0) { "MIM crafting complexity must be positive" }
    }

    fun sameAs(other: MIMCraftingPattern): Boolean = key == other.key &&
        ItemStack.matches(result, other.result) && inputs.size == other.inputs.size &&
        inputs.indices.all { ItemStack.matches(inputs[it], other.inputs[it]) } &&
        remainders.size == other.remainders.size && remainders.indices.all { ItemStack.matches(remainders[it], other.remainders[it]) } &&
        isolatedOutputs == other.isolatedOutputs && complexity == other.complexity && work == other.work
}

data class MIMCraftingWork(val ticks: Int, val mruPerTick: Int) {
    init {
        require(ticks > 0 && mruPerTick >= 0) { "Invalid MIM crafting work" }
    }
    val totalMRU: Long get() = ticks.toLong() * mruPerTick
}

object MIMFrames {
    private class Adapter(val accepts: (ItemStack) -> Boolean, val provider: MIMFrameProvider)
    private val adapters = linkedMapOf<Identifier, Adapter>()

    @JvmStatic
    fun register(id: Identifier, accepts: (ItemStack) -> Boolean, provider: MIMFrameProvider) {
        check(adapters.putIfAbsent(id, Adapter(accepts, provider)) == null) { "Duplicate MIM frame adapter: $id" }
    }

    @JvmStatic
    fun provider(stack: ItemStack): MIMFrameProvider? = if (stack.isEmpty) null
    else stack.item as? MIMFrameProvider ?: adapters.values.firstOrNull { it.accepts(stack) }?.provider

    @JvmStatic
    fun patterns(level: ServerLevel, stack: ItemStack): List<MIMCraftingPattern> =
        provider(stack)?.patterns(level, stack.copy()).orEmpty()
}
