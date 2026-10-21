package com.algorithmlx.ecr.api.inventory

import com.algorithmlx.ecr.api.inventory.PlatformItemContainer
import net.minecraft.world.item.ItemStack

data class StoredItem(val stack: ItemStack, val count: Long)

class InventoryNetwork(endpoints: List<InventoryEndpoint>) {
    val endpoints: List<InventoryEndpoint> = endpoints.toList()

    fun isFull(): Boolean {
        var hasSlots = false
        for (endpoint in endpoints) for (slot in endpoint.slots()) {
            val stack = endpoint.container.getItem(slot)
            if (stack.isEmpty) return false
            val capacity = endpoint.totalCapacity(slot, stack)
            if (capacity <= 0) continue
            hasSlots = true
            val amount = (endpoint.container as? PlatformItemContainer)?.amount(slot) ?: stack.count.toLong()
            if (amount < capacity) return false
        }
        return hasSlots
    }

    fun snapshot(): List<StoredItem> {
        val counts = linkedMapOf<ItemStackKey, Long>()
        val visited = hashSetOf<InventorySlot>()
        for (endpoint in endpoints) for (slot in endpoint.slots()) {
            val stack = endpoint.container.getItem(slot)
            if (stack.isEmpty || !endpoint.canExtract(slot, stack)) continue
            if (!visited.add(endpoint.physicalSlot(slot))) continue
            val key = ItemStackKey(stack)
            val amount = (endpoint.container as? PlatformItemContainer)?.amount(slot) ?: stack.count.toLong()
            counts[key] = (counts[key] ?: 0L) + amount
        }
        return counts.map { (key, count) -> StoredItem(key.stack.copy(), count) }
    }

    fun insert(stack: ItemStack, simulate: Boolean = false): ItemStack {
        return InventoryTransaction().use { transaction ->
            val remainder = transaction.insert(endpoints, stack)
            if (simulate || transaction.commit()) remainder else stack.copy()
        }
    }

    fun extract(template: ItemStack, amount: Int, simulate: Boolean = false): ItemStack {
        return InventoryTransaction().use { transaction ->
            val result = transaction.extract(endpoints, template, amount.coerceAtLeast(0))
            if (simulate || transaction.commit()) result else ItemStack.EMPTY
        }
    }

    fun transact(
        inputs: List<ItemStack>, outputs: List<ItemStack>,
        isolatedOutputs: Set<Int> = emptySet(),
        prepareOutputs: ((List<ItemStack>) -> List<ItemStack>)? = null
    ): Boolean {
        if (prepareOutputs == null) return plan(inputs, outputs, isolatedOutputs) { it.commit() } == true
        if (plan(inputs, outputs, isolatedOutputs) { true } != true) return false
        val prepared = prepareOutputs(outputs.map(ItemStack::copy))
        return plan(inputs, prepared) { it.commit() } == true
    }

    fun canTransact(inputs: List<ItemStack>, outputs: List<ItemStack>, isolatedOutputs: Set<Int> = emptySet()): Boolean =
        plan(inputs, outputs, isolatedOutputs) { true } == true

    private fun <T> plan(
        inputs: List<ItemStack>, outputs: List<ItemStack>, isolatedOutputs: Set<Int> = emptySet(),
        action: (InventoryTransaction) -> T
    ): T? = InventoryTransaction().use { transaction ->
        for (input in inputs) {
            if (input.isEmpty) continue
            if (transaction.extract(endpoints, input, input.count).count != input.count) return null
        }
        for ((index, output) in outputs.withIndex()) {
            if (!transaction.insert(endpoints, output, mergeExisting = index !in isolatedOutputs).isEmpty) return null
        }
        action(transaction)
    }

    private class ItemStackKey(stack: ItemStack) {
        val stack = stack.copyWithCount(1)
        override fun equals(other: Any?): Boolean =
            other is ItemStackKey && ItemStack.isSameItemSameComponents(stack, other.stack)
        override fun hashCode(): Int = ItemStack.hashItemAndComponents(stack)
    }
}
