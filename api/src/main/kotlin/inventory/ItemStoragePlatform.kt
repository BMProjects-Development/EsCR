package com.algorithmlx.ecr.api.inventory

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

interface ItemStorageTransaction : AutoCloseable {
    fun commit()
}

interface ItemStoragePlatform {
    fun find(level: Level, pos: BlockPos, side: Direction?): PlatformItemContainer?
    fun openTransaction(): ItemStorageTransaction

    companion object {
        @JvmStatic
        var instance: ItemStoragePlatform? = null
    }
}

abstract class PlatformItemContainer : Container {
    abstract val storageIdentity: Any
    abstract fun slotIdentity(slot: Int): Any
    abstract fun amount(slot: Int): Long
    abstract fun capacity(slot: Int, stack: ItemStack): Int
    open fun totalCapacity(slot: Int, stack: ItemStack): Long = capacity(slot, stack).toLong()
    abstract fun insert(stack: ItemStack, transaction: ItemStorageTransaction, slot: Int? = null, mergeExisting: Boolean = true): Int
    abstract fun extract(slot: Int, stack: ItemStack, amount: Int, transaction: ItemStorageTransaction): Int

    override fun isEmpty(): Boolean = (0 until containerSize).all { amount(it) == 0L }
    override fun getMaxStackSize(): Int = Int.MAX_VALUE
    override fun getMaxStackSize(stack: ItemStack): Int = Int.MAX_VALUE
    override fun setChanged() {}
    override fun stillValid(player: Player): Boolean = true

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean =
        !stack.isEmpty && requireNotNull(ItemStoragePlatform.instance).openTransaction().use { insert(stack.copyWithCount(1), it, slot) > 0 }

    override fun canTakeItem(destination: Container, slot: Int, stack: ItemStack): Boolean =
        !stack.isEmpty && requireNotNull(ItemStoragePlatform.instance).openTransaction().use { extract(slot, stack, 1, it) > 0 }

    final override fun setItem(slot: Int, stack: ItemStack): Unit = unsupportedMutation()
    final override fun removeItem(slot: Int, amount: Int): ItemStack = unsupportedMutation()
    final override fun removeItemNoUpdate(slot: Int): ItemStack = unsupportedMutation()
    final override fun clearContent(): Unit = unsupportedMutation()

    private fun unsupportedMutation(): Nothing = error("Platform inventories require an item storage transaction")
}

class ItemStorageIdentity(private val owner: Any, private val slot: Int = 0) {
    override fun equals(other: Any?): Boolean = other is ItemStorageIdentity && owner === other.owner && slot == other.slot
    override fun hashCode(): Int = 31 * System.identityHashCode(owner) + slot
}
