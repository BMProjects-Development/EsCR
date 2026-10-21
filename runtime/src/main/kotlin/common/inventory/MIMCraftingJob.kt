package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.inventory.StoredItem
import net.minecraft.world.item.ItemStack
import com.algorithmlx.ecr.api.mim.MIMCraftingPattern

internal class MIMCraftingJob(patterns: List<MIMCraftingPattern>, initialStock: List<StoredItem>) {
    enum class Status { SEARCHING, CRAFTED, EXHAUSTED }
    private sealed interface Step
    private data object Progress : Step
    private data object Exhausted : Step
    private class Attempt(val pattern: MIMCraftingPattern, grid: List<ItemStack>) : Step {
        val grid = grid.map(ItemStack::copy)
    }
    private val choices = patterns.toList()
    private val stored = initialStock.map { StoredItem(it.stack.copy(), it.count) }
    private var finished: Status? = null
    private var retry: Attempt? = null
    enum class Decision { REJECTED, WAITING, CRAFTED }

    private val search = sequence<Step> {
        for (pattern in choices) {
            val grid = pattern.ingredients.map(ItemStack::copy).toMutableList()
            val occupied = grid.indices.filter { !grid[it].isEmpty }
            val candidates = mutableMapOf<Int, MutableList<Int>>()
            for (slot in occupied) {
                val matches = mutableListOf<Int>()
                for (index in stored.indices) {
                    if (stored[index].count >= grid[slot].count && pattern.accepts(slot, stored[index].stack.copy())) matches += index
                    yield(Progress)
                }
                candidates[slot] = matches
            }
            if (occupied.isEmpty()) {
                yield(Attempt(pattern, grid))
                continue
            }
            if (candidates.values.any { it.isEmpty() }) continue
            val slots = occupied.sortedBy { candidates.getValue(it).size }
            val available = LongArray(stored.size) { stored[it].count }
            val cursors = IntArray(slots.size)
            val selected = IntArray(slots.size) { -1 }
            var depth = 0
            while (depth >= 0) {
                if (depth == slots.size) {
                    yield(Attempt(pattern, grid))
                    depth--
                    if (depth >= 0) available[selected[depth]] += grid[slots[depth]].count
                    yield(Progress)
                    continue
                }
                val options = candidates.getValue(slots[depth])
                if (cursors[depth] == options.size) {
                    cursors[depth] = 0
                    selected[depth] = -1
                    depth--
                    if (depth >= 0) available[selected[depth]] += grid[slots[depth]].count
                } else {
                    val index = options[cursors[depth]++]
                    val amount = grid[slots[depth]].count
                    if (available[index] >= amount) {
                        available[index] -= amount
                        selected[depth] = index
                        grid[slots[depth]] = stored[index].stack.copyWithCount(amount)
                        depth++
                    }
                }
                yield(Progress)
            }
        }
        yield(Exhausted)
    }.iterator()

    fun advance(budget: Int, commit: (MIMCraftingPattern, List<ItemStack>, List<ItemStack>) -> Decision): Status {
        finished?.let { return it }
        var remaining = budget.coerceAtLeast(0)
        if (retry != null && remaining > 0) {
            remaining--
            when (tryCommit(retry!!, commit)) {
                Decision.CRAFTED -> return Status.CRAFTED.also { finished = it }
                Decision.WAITING -> return Status.SEARCHING
                Decision.REJECTED -> retry = null
            }
        }
        repeat(remaining) {
            when (val step = if (search.hasNext()) search.next() else Exhausted) {
                Progress -> Unit
                Exhausted -> return Status.EXHAUSTED.also { finished = it }
                is Attempt -> when (tryCommit(step, commit)) {
                    Decision.CRAFTED -> return Status.CRAFTED.also { finished = it }
                    Decision.WAITING -> {
                        retry = step
                        return Status.SEARCHING
                    }
                    Decision.REJECTED -> Unit
                }
            }
        }
        return Status.SEARCHING
    }

    private fun tryCommit(
        attempt: Attempt, commit: (MIMCraftingPattern, List<ItemStack>, List<ItemStack>) -> Decision
    ): Decision {
        val outputs = attempt.pattern.outputs(attempt.grid.map(ItemStack::copy))?.filterNot(ItemStack::isEmpty) ?: return Decision.REJECTED
        if (outputs.isEmpty() || !ItemStack.matches(attempt.pattern.output, outputs.first())) return Decision.REJECTED
        val inputs = attempt.grid.filterNot(ItemStack::isEmpty).map(ItemStack::copy)
        return commit(attempt.pattern, inputs, outputs.map(ItemStack::copy))
    }
}
