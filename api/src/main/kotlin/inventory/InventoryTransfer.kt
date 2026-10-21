package com.algorithmlx.ecr.api.inventory

import net.minecraft.world.item.ItemStack

object InventoryTransfer {
    fun canMove(source: InventoryEndpoint, dest: InventoryEndpoint, filter: (ItemStack) -> Boolean = { true }): Boolean {
        if (InventoryAccess.overlaps(source, dest)) return false
        return InventoryTransaction().use { stage(it, source, listOf(dest), 1, 0, filter) > 0 }
    }

    fun move(source: InventoryEndpoint, dest: InventoryEndpoint, limit: Int, filter: (ItemStack) -> Boolean = { true }): Int {
        if (limit <= 0 || InventoryAccess.overlaps(source, dest)) return 0
        return move(source, listOf(dest), limit, 0, filter)
    }

    fun moveToNetwork(source: InventoryEndpoint, network: InventoryNetwork, retain: Int, filter: (ItemStack) -> Boolean): Int {
        val destinations = network.endpoints.filterNot { InventoryAccess.overlaps(source, it) }
        if (destinations.isEmpty()) return 0
        return move(source, destinations, Int.MAX_VALUE, retain, filter)
    }

    fun moveFromNetwork(network: InventoryNetwork, dest: InventoryEndpoint, persistent: Boolean, filter: (ItemStack) -> Boolean): Int {
        val sources = network.endpoints.filterNot { InventoryAccess.overlaps(it, dest) }
        if (sources.isEmpty()) return 0
        if (!persistent) return sources.sumOf { move(it, listOf(dest), Int.MAX_VALUE, 0, filter) }
        var moved = 0

        for (slot in dest.slots()) {
            if (!dest.container.getItem(slot).isEmpty) continue
            for (source in sources) {
                val candidate = source.slots().firstOrNull {
                    val stack = source.container.getItem(it)
                    !stack.isEmpty && filter(stack) && source.canExtract(it, stack, dest.container) && dest.canInsert(slot, stack)
                } ?: continue
                val stack = source.container.getItem(candidate)
                if (dest.container.getMaxStackSize(stack) < 1) continue
                InventoryTransaction().use { transaction ->
                    transaction.set(dest, slot, stack.copyWithCount(1))
                    transaction.set(source, candidate, stack.copyWithCount(stack.count - 1))
                    if (transaction.commit()) moved++
                }
                break
            }
        }
        return moved
    }

    private fun move(
        source: InventoryEndpoint, destinations: List<InventoryEndpoint>, limit: Int, retain: Int, filter: (ItemStack) -> Boolean
    ): Int {
        return InventoryTransaction().use { transaction ->
            val count = stage(transaction, source, destinations, limit, retain, filter)
            if (count > 0 && transaction.commit()) count else 0
        }
    }

    private fun stage(
        transaction: InventoryTransaction, source: InventoryEndpoint, destinations: List<InventoryEndpoint>,
        limit: Int, retain: Int, filter: (ItemStack) -> Boolean
    ): Int {
        var moved = 0
        for (slot in source.slots()) {
            val stack = transaction.get(source, slot)
            if (stack.isEmpty || !filter(stack)) continue
            val allowedDestinations = destinations.filter { source.canExtract(slot, stack, it.container) }
            if (allowedDestinations.isEmpty()) continue
            val offered = minOf((stack.count - retain).coerceAtLeast(0), limit - moved)
            if (offered == 0) continue
            val remainder = transaction.insert(allowedDestinations, stack.copyWithCount(offered))
            val accepted = offered - remainder.count
            transaction.set(source, slot, stack.copyWithCount(stack.count - accepted))
            moved += accepted
            if (moved == limit) break
        }
        return moved
    }
}
