package com.algorithmlx.ecr.api.inventory

import com.algorithmlx.ecr.api.inventory.ItemStoragePlatform
import com.algorithmlx.ecr.api.inventory.ItemStorageTransaction
import com.algorithmlx.ecr.api.inventory.PlatformItemContainer
import net.minecraft.world.Container
import net.minecraft.world.item.ItemStack
import java.util.Collections
import java.util.IdentityHashMap

internal class InventoryTransaction : AutoCloseable {
    private class SlotState(val slot: InventorySlot) {
        val before: ItemStack = slot.container.getItem(slot.index).copy()
        var after: ItemStack = before.copy()
    }

    private val states = linkedMapOf<InventorySlot, SlotState>()
    private var external: ItemStorageTransaction? = null
    private var failed = false

    private fun externalTransaction(): ItemStorageTransaction = external ?: requireNotNull(ItemStoragePlatform.instance)
        .openTransaction().also { external = it }

    private fun state(endpoint: InventoryEndpoint, slot: Int): SlotState {
        val physicalSlot = endpoint.physicalSlot(slot)
        return states.getOrPut(physicalSlot) { SlotState(physicalSlot) }
    }

    fun get(endpoint: InventoryEndpoint, slot: Int): ItemStack =
        if (endpoint.container is PlatformItemContainer) endpoint.container.getItem(slot) else state(endpoint, slot).after

    fun set(endpoint: InventoryEndpoint, slot: Int, stack: ItemStack) {
        val storage = endpoint.container as? PlatformItemContainer
        if (storage != null) {
            val before = storage.getItem(slot)
            val sameItem = ItemStack.isSameItemSameComponents(before, stack)
            val remove = if (sameItem) (before.count - stack.count).coerceAtLeast(0) else before.count
            val insert = if (sameItem) (stack.count - before.count).coerceAtLeast(0) else stack.count
            val transaction = externalTransaction()
            if (remove > 0 && storage.extract(slot, before, remove, transaction) != remove) failed = true
            if (insert > 0 && storage.insert(stack.copyWithCount(insert), transaction, slot) != insert) failed = true
            return
        }
        state(endpoint, slot).after = stack.copy()
    }

    fun insert(endpoints: List<InventoryEndpoint>, stack: ItemStack, mergeExisting: Boolean = true): ItemStack {
        val remainder = stack.copy()
        if (remainder.isEmpty) return ItemStack.EMPTY

        for (endpoint in endpoints) {
            val storage = endpoint.container as? PlatformItemContainer
            if (storage != null) {
                remainder.shrink(storage.insert(remainder, externalTransaction(), mergeExisting = mergeExisting))
                if (remainder.isEmpty) return ItemStack.EMPTY
                continue
            }
            for (empty in listOf(false, true)) for (slot in endpoint.slots()) {
                if (!empty && !mergeExisting) continue
                val current = get(endpoint, slot)
                if (current.isEmpty != empty || !endpoint.canInsert(slot, remainder)) continue
                if (!current.isEmpty && !ItemStack.isSameItemSameComponents(current, remainder)) continue
                val limit = minOf(remainder.maxStackSize, endpoint.maxStackSize(slot, remainder))
                val count = minOf(remainder.count, (limit - current.count).coerceAtLeast(0))
                if (count == 0) continue
                set(endpoint, slot, remainder.copyWithCount(current.count + count))
                remainder.shrink(count)
                if (remainder.isEmpty) return ItemStack.EMPTY
            }
        }
        return remainder
    }

    fun extract(endpoints: List<InventoryEndpoint>, template: ItemStack, amount: Int): ItemStack {
        if (template.isEmpty || amount <= 0) return ItemStack.EMPTY
        var remaining = amount
        for (endpoint in endpoints) for (slot in endpoint.slots()) {
            val current = get(endpoint, slot)
            if (current.isEmpty || !ItemStack.isSameItemSameComponents(template, current) ||
                !endpoint.canExtract(slot, current))
                continue
            val taken = minOf(remaining, current.count)
            set(endpoint, slot, current.copyWithCount(current.count - taken))
            remaining -= taken
            if (remaining == 0) return template.copyWithCount(amount)
        }
        return if (remaining == amount) ItemStack.EMPTY else template.copyWithCount(amount - remaining)
    }

    fun commit(): Boolean {
        if (failed || states.values.any { !ItemStack.matches(it.before, it.slot.container.getItem(it.slot.index)) }) {
            close()
            return false
        }
        val changed = states.values.filter { !ItemStack.matches(it.before, it.after) }
        changed.forEach { it.slot.container.setItem(it.slot.index, it.after.copy()) }
        val containers = Collections.newSetFromMap(IdentityHashMap<Container, Boolean>())
        changed.forEach { containers.add(it.slot.container) }
        containers.forEach(Container::setChanged)
        external?.commit()
        close()
        return true
    }

    override fun close() {
        external?.close()
        external = null
    }
}
