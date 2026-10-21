package com.algorithmlx.ecr.api.mim

import com.algorithmlx.ecr.api.inventory.StoredItem
import net.minecraft.world.item.ItemStack

enum class MIMDependencyStatus { STORED, CRAFTABLE, MISSING, CYCLE, LIMIT }

class MIMDependencyNode(
    val stack: ItemStack, val amount: Long, val stored: Long, val crafts: Int,
    val status: MIMDependencyStatus, val children: List<MIMDependencyNode> = emptyList()
)

class MIMCraftingPlan(val root: MIMDependencyNode, val steps: List<Step>, val complete: Boolean) {
    class Step(val pattern: MIMCraftingPattern, val executions: Int)
}

class MIMCraftingPlanner(
    patterns: List<MIMCraftingPattern>, stock: List<StoredItem>,
    private val maxNodes: Int, private val maxDepth: Int, private val maxCrafts: Int
) {
    private val recipes = patterns.toList()
    private val initialStock = stock.map { StoredItem(it.stack.copyWithCount(1), it.count) }

    init {
        require(maxNodes > 0 && maxDepth > 0 && maxCrafts > 0) { "Invalid MIM planner budget" }
    }

    fun plan(target: ItemStack, batches: Int): MIMCraftingPlan {
        require(!target.isEmpty && batches > 0) { "A MIM crafting request must have an item and a positive batch count" }
        return Search().run(target.copyWithCount(1), batches)
    }

    private inner class Search {
        private val stock = linkedMapOf<Key, Long>()
        private val steps = mutableListOf<MIMCraftingPlan.Step>()
        private var nodes = 1
        private var crafts = 0

        init {
            initialStock.forEach { add(it.stack, it.count) }
        }

        fun run(target: ItemStack, batches: Int): MIMCraftingPlan {
            val candidates = recipes.filter { ItemStack.isSameItemSameComponents(it.output, target) }
            val amount = (candidates.firstOrNull()?.output?.count ?: 1).toLong() * batches
            val starting = snapshot()
            var failure: MIMDependencyNode? = null
            for (pattern in candidates) {
                restore(starting)
                val result = produce(pattern, amount, 0, emptySet(), 0)
                if (result.status == MIMDependencyStatus.CRAFTABLE)
                    return MIMCraftingPlan(result, steps.toList(), true)
                failure = prefer(failure, result)
            }
            return MIMCraftingPlan(failure ?: MIMDependencyNode(target, amount, 0, 0, MIMDependencyStatus.MISSING), emptyList(), false)
        }

        private fun ingredient(
            template: ItemStack, amount: Long, accepts: (ItemStack) -> Boolean, path: Set<Key>, depth: Int
        ): MIMDependencyNode {
            if (++nodes > maxNodes || depth > maxDepth)
                return MIMDependencyNode(template, amount, 0, 0, MIMDependencyStatus.LIMIT)
            val supplied = consume(template, amount, template.count, accepts)
            if (supplied == amount) return MIMDependencyNode(template, amount, supplied, 0, MIMDependencyStatus.STORED)
            val starting = snapshot()
            var failure: MIMDependencyNode? = null
            for (pattern in recipes.filter { accepts(it.output) }) {
                restore(starting)
                val partial = minOf(stock[Key(pattern.output)] ?: 0L, amount - supplied)
                val result = produce(pattern, amount - supplied - partial, supplied + partial, path, depth)
                if (result.status == MIMDependencyStatus.CRAFTABLE) {
                    val consumed = consume(pattern.output, amount - supplied, template.count, accepts)
                    if (consumed == amount - supplied)
                        return MIMDependencyNode(result.stack, amount, supplied + partial, result.crafts, result.status, result.children)
                }
                failure = prefer(failure, result)
            }
            restore(starting)
            return failure ?: MIMDependencyNode(template, amount, supplied, 0, MIMDependencyStatus.MISSING)
        }

        private fun produce(
            pattern: MIMCraftingPattern, amount: Long, supplied: Long, path: Set<Key>, depth: Int
        ): MIMDependencyNode {
            val output = pattern.output
            val key = Key(output)
            if (key in path) return MIMDependencyNode(output, amount + supplied, supplied, 0, MIMDependencyStatus.CYCLE)
            val required = (amount - 1) / output.count + 1
            if (depth > maxDepth || nodes > maxNodes || required > maxCrafts - crafts)
                return MIMDependencyNode(output, amount + supplied, supplied, 0, MIMDependencyStatus.LIMIT)
            crafts += required.toInt()
            val nextPath = path + key
            val children = mutableListOf<MIMDependencyNode>()
            var limited = false
            for ((index, template) in pattern.ingredients.withIndex()) {
                if (template.isEmpty) continue
                if (nodes >= maxNodes) {
                    limited = true
                    break
                }
                children += ingredient(template, template.count.toLong() * required, { pattern.accepts(index, it) }, nextPath, depth + 1)
            }
            val complete = !limited && children.all { it.status == MIMDependencyStatus.STORED || it.status == MIMDependencyStatus.CRAFTABLE }
            if (!complete) {
                val status = if (limited) MIMDependencyStatus.LIMIT else children.firstOrNull { it.status == MIMDependencyStatus.LIMIT }?.status
                    ?: children.firstOrNull { it.status == MIMDependencyStatus.MISSING }?.status ?: MIMDependencyStatus.CYCLE
                return MIMDependencyNode(output, amount + supplied, supplied, required.toInt(), status, children)
            }
            pattern.guaranteedOutputs.forEach { add(it, it.count.toLong() * required) }
            steps += MIMCraftingPlan.Step(pattern, required.toInt())
            return MIMDependencyNode(output, amount + supplied, supplied, required.toInt(), MIMDependencyStatus.CRAFTABLE, children)
        }

        private fun consume(template: ItemStack, amount: Long, unit: Int, accepts: (ItemStack) -> Boolean): Long {
            var missing = amount
            val candidates = stock.keys.sortedBy { if (ItemStack.isSameItemSameComponents(it.stack, template)) 0 else 1 }
            for (key in candidates) {
                if (!accepts(key.stack.copy())) continue
                val available = stock.getValue(key)
                val count = minOf(available / unit * unit, missing)
                stock[key] = available - count
                missing -= count
                if (missing == 0L) break
            }
            return amount - missing
        }

        private fun add(stack: ItemStack, amount: Long) {
            if (stack.isEmpty || amount <= 0) return
            val key = Key(stack)
            val current = stock[key] ?: 0
            stock[key] = if (Long.MAX_VALUE - current < amount) Long.MAX_VALUE else current + amount
        }

        private fun snapshot(): Snapshot = Snapshot(LinkedHashMap(stock), steps.size, crafts)

        private fun restore(snapshot: Snapshot) {
            stock.clear()
            stock.putAll(snapshot.stock)
            while (steps.size > snapshot.steps) steps.removeLast()
            crafts = snapshot.crafts
        }

        private fun prefer(previous: MIMDependencyNode?, next: MIMDependencyNode): MIMDependencyNode =
            if (previous == null || previous.status == MIMDependencyStatus.CYCLE && next.status != MIMDependencyStatus.CYCLE) next else previous
    }

    private class Snapshot(val stock: Map<Key, Long>, val steps: Int, val crafts: Int)

    private class Key(stack: ItemStack) {
        val stack = stack.copyWithCount(1)
        override fun equals(other: Any?): Boolean = other is Key && ItemStack.isSameItemSameComponents(stack, other.stack)
        override fun hashCode(): Int = ItemStack.hashItemAndComponents(stack)
    }
}
