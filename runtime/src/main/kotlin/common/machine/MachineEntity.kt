package com.algorithmlx.ecr.common.machine

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.Containers
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

abstract class MachineEntity(type: BlockEntityType<*>, pos: BlockPos, state: BlockState, size: Int): BlockEntity(type, pos, state), WorldlyContainer {
    protected val items: NonNullList<ItemStack> = NonNullList.withSize(size, ItemStack.EMPTY)
    private val slots = IntArray(size) { it }

    override fun getContainerSize(): Int = items.size
    override fun isEmpty(): Boolean = items.all(ItemStack::isEmpty)
    override fun getItem(slot: Int): ItemStack = items[slot]
    override fun removeItem(slot: Int, amount: Int): ItemStack = ContainerHelper.removeItem(items, slot, amount).also { if (!it.isEmpty) setChanged() }
    override fun removeItemNoUpdate(slot: Int): ItemStack = ContainerHelper.takeItem(items, slot)
    override fun setItem(slot: Int, stack: ItemStack) {
        items[slot] = stack
        stack.limitSize(minOf(maxStackSize, stack.maxStackSize))
        setChanged()
    }
    override fun clearContent() {
        items.clear()
        setChanged()
    }
    override fun stillValid(player: Player): Boolean = Container.stillValidBlockEntity(this, player)
    override fun getSlotsForFace(direction: Direction): IntArray = slots
    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, direction: Direction?): Boolean = canPlaceItem(slot, stack)
    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, direction: Direction): Boolean = true

    open fun serverTick(level: ServerLevel) = Unit

    protected fun canFit(slot: Int, result: ItemStack): Boolean {
        val current = items[slot]
        val limit = minOf(maxStackSize, result.maxStackSize)
        return result.count <= limit && (current.isEmpty || ItemStack.isSameItemSameComponents(current, result) && current.count <= limit - result.count)
    }

    protected fun putResult(slot: Int, result: ItemStack) {
        check(canFit(slot, result))
        if (items[slot].isEmpty) items[slot] = result.copy() else items[slot].grow(result.count)
        setChanged()
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        ContainerHelper.saveAllItems(output, items)
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        items.clear()
        ContainerHelper.loadAllItems(input, items)
    }

    override fun preRemoveSideEffects(pos: BlockPos, state: BlockState) {
        val level = level
        if (level != null && !level.isClientSide) {
            Containers.dropContents(level, pos, this)
            items.clear()
        }
        super.preRemoveSideEffects(pos, state)
    }
}
