package com.algorithmlx.ecr.common.machine

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

abstract class MachineEntity(
    type: BlockEntityType<*>, pos: BlockPos, state: BlockState, size: Int, synchronize: Boolean = false, hasMenu: Boolean = false
): MachineContainerEntity(type, pos, state, size, synchronize, hasMenu), WorldlyContainer {
    private val slots = IntArray(size) { it }

    override fun clearContent() {
        super.clearContent()
        setChanged()
    }
    override fun getSlotsForFace(direction: Direction): IntArray = slots
    override fun canPlaceItemThroughFace(slot: Int, stack: ItemStack, direction: Direction?): Boolean = canPlaceItem(slot, stack)
    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, direction: Direction): Boolean = true

    protected fun canFit(slot: Int, result: ItemStack): Boolean {
        val current = items[slot]
        val limit = getMaxStackSize(result)
        return result.count <= limit && (current.isEmpty || ItemStack.isSameItemSameComponents(current, result) && current.count <= limit - result.count)
    }

    protected fun putResult(slot: Int, result: ItemStack) {
        check(canFit(slot, result))
        if (items[slot].isEmpty) items[slot] = result.copy() else items[slot].grow(result.count)
        setChanged()
    }
}
