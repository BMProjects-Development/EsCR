package com.algorithmlx.ecr.common.menu

import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.ContainerUser
import net.minecraft.world.item.ItemStack

class MIMInventoryWindow(
    private val source: Container, private val size: Int, private val columns: Int = MIMTerminalLayout.COLUMNS,
    private val fixedSlots: Int = 0
) : Container {
    var row = 0
        private set
    val firstSlot: Int get() = fixedSlots + row * columns

    fun select(row: Int) { this.row = row.coerceAtLeast(0) }
    fun sourceSlot(index: Int): Int = if (index < fixedSlots) index else firstSlot + index - fixedSlots
    override fun getContainerSize(): Int = size
    override fun isEmpty(): Boolean = (0 until size).all { getItem(it).isEmpty }
    override fun getItem(index: Int): ItemStack = sourceSlot(index).let { if (it < source.containerSize) source.getItem(it) else ItemStack.EMPTY }
    override fun removeItem(index: Int, amount: Int): ItemStack = source.removeItem(sourceSlot(index), amount)
    override fun removeItemNoUpdate(index: Int): ItemStack = source.removeItemNoUpdate(sourceSlot(index))
    override fun setItem(index: Int, stack: ItemStack) { source.setItem(sourceSlot(index), stack) }
    override fun canPlaceItem(index: Int, stack: ItemStack): Boolean = source.canPlaceItem(sourceSlot(index), stack)
    override fun canTakeItem(destination: Container, index: Int, stack: ItemStack): Boolean = source.canTakeItem(destination, sourceSlot(index), stack)
    override fun getMaxStackSize(): Int = source.maxStackSize
    override fun getMaxStackSize(stack: ItemStack): Int = source.getMaxStackSize(stack)
    override fun setChanged() { source.setChanged() }
    override fun startOpen(user: ContainerUser) { source.startOpen(user) }
    override fun stopOpen(user: ContainerUser) { source.stopOpen(user) }
    override fun getEntitiesWithContainerOpen(): List<ContainerUser> = source.entitiesWithContainerOpen
    override fun stillValid(player: Player): Boolean = source.stillValid(player)
    override fun clearContent() { repeat(size) { setItem(it, ItemStack.EMPTY) } }
}
