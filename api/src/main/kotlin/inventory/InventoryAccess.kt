package com.algorithmlx.ecr.api.inventory

import com.algorithmlx.ecr.api.inventory.ItemStoragePlatform
import com.algorithmlx.ecr.api.inventory.PlatformItemContainer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.Container
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.ChestBlock
import net.minecraft.world.level.block.entity.HopperBlockEntity
import net.minecraft.world.level.block.state.properties.ChestType

class InventoryEndpoint(
    val container: Container,
    val side: Direction? = null,
    val identities: Set<Container> = setOf(container)
) {
    fun slots(): IntArray = (0 until container.containerSize).filter { slot ->
        InventorySlot.path(container, slot).all(::accessible)
    }.toIntArray()

    fun canInsert(slot: Int, stack: ItemStack): Boolean = InventorySlot.path(container, slot).all { view ->
        accessible(view) && view.container.canPlaceItem(view.index, stack) &&
            (side == null || view.container !is WorldlyContainer || view.container.canPlaceItemThroughFace(view.index, stack, side))
    }

    fun canExtract(slot: Int, stack: ItemStack, destination: Container = container): Boolean =
        InventorySlot.path(container, slot).all { view ->
            accessible(view) && view.container.canTakeItem(destination, view.index, stack) &&
                (side == null || view.container !is WorldlyContainer || view.container.canTakeItemThroughFace(view.index, stack, side))
        }

    internal fun physicalSlot(slot: Int): InventorySlot = InventorySlot.path(container, slot).last()

    internal fun maxStackSize(slot: Int, stack: ItemStack): Int =
        InventorySlot.path(container, slot).minOf { view ->
            (view.container as? PlatformItemContainer)?.capacity(view.index, stack) ?: view.container.getMaxStackSize(stack)
        }

    internal fun totalCapacity(slot: Int, stack: ItemStack): Long =
        InventorySlot.path(container, slot).minOf { view ->
            (view.container as? PlatformItemContainer)?.totalCapacity(view.index, stack)
                ?: minOf(stack.maxStackSize, view.container.getMaxStackSize(stack)).toLong()
        }

    private fun accessible(view: InventorySlot): Boolean = side == null || view.container !is WorldlyContainer ||
        view.index in view.container.getSlotsForFace(side)
}

object InventoryAccess {
    fun resolve(level: Level, pos: BlockPos, side: Direction? = null): InventoryEndpoint? {
        if (!level.isLoaded(pos)) return null
        val state = level.getBlockState(pos)
        val identities = linkedSetOf<Container>()
        (level.getBlockEntity(pos) as? Container)?.let(identities::add)
        if (state.block is ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            val neighbor = ChestBlock.getConnectedBlockPos(pos, state)
            if (!level.isLoaded(neighbor)) return null
            (level.getBlockEntity(neighbor) as? Container)?.let(identities::add)
        }
        val inventory = HopperBlockEntity.getContainerAt(level, pos)
            ?: ItemStoragePlatform.instance?.find(level, pos, side) ?: return null
        if (identities.isEmpty()) identities.add(inventory)
        return InventoryEndpoint(inventory, side, identities)
    }

    fun overlaps(first: InventoryEndpoint, second: InventoryEndpoint): Boolean =
        containers(first).any { a -> containers(second).any { b ->
            a === b || a is PlatformItemContainer && b is PlatformItemContainer && a.storageIdentity == b.storageIdentity
        } }

    private fun containers(endpoint: InventoryEndpoint): List<Container> =
        InventorySlot.containers(endpoint.container) + endpoint.identities.flatMap { InventorySlot.containers(it) }
}
