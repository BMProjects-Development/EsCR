package com.algorithmlx.ecr.common.menu

import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.item.ItemStack

internal class MIMSlotLayout(val displaySlots: Int, val deviceSlots: Int) {
    init {
        require(displaySlots >= 0 && deviceSlots >= 0)
    }

    val playerStart = displaySlots + deviceSlots
    val hotbarStart = playerStart + Inventory.INVENTORY_SIZE - Inventory.getSelectionSize()
    val end = playerStart + Inventory.INVENTORY_SIZE

    fun transfer(
        stack: ItemStack,
        source: Int,
        move: (ItemStack, Int, Int, Boolean) -> Boolean
    ): Boolean = when (source) {
        in displaySlots..<playerStart -> move(stack, playerStart, end, true)
        in playerStart..<end -> move(stack, displaySlots, playerStart, false) || transferWithinPlayerInventory(stack, source, move)
        else -> false
    }

    fun transferWithinPlayerInventory(
        stack: ItemStack,
        source: Int,
        move: (ItemStack, Int, Int, Boolean) -> Boolean
    ): Boolean = when (source) {
        in playerStart..<hotbarStart -> move(stack, hotbarStart, end, false)
        in hotbarStart..<end -> move(stack, playerStart, hotbarStart, false)
        else -> false
    }
}
