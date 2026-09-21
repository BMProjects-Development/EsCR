package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.container.AbstractMenu
import com.algorithmlx.ecr.api.container.slot.VanillaSpecialSlot
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.menu.MenuTypeData
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockPartEntity
import com.algorithmlx.ecr.common.api.BoundGemHelper
import com.algorithmlx.ecr.registry.BlockRegistry
import com.algorithmlx.ecr.registry.MenuTypeRegistry
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

class RayTowerMenu(
    containerId: Int,
    inv: Inventory,
    container: Container,
    val blockEntity: BlockEntity?,
    access: ContainerLevelAccess
) : AbstractMenu(MenuTypeRegistry.rayTower, containerId, access) {
    constructor(containerId: Int, inv: Inventory, typeData: MenuTypeData) : this(
        containerId,
        inv,
        SimpleContainer(1),
        inv.player.level().getBlockEntity(typeData.pos),
        ContainerLevelAccess.NULL
    )

    init {
        addSlot(
            VanillaSpecialSlot(container, 0, 80, 48, { stack ->
                val item = stack.item
                isAssembled() && item is BoundGem && BoundGemHelper.getBoundPos(stack) != null
            }, { _ -> isAssembled() })
        )

        inv.make()
    }

    override fun quickMoveStack(
        player: Player,
        slotIndex: Int
    ): ItemStack {
        val slot = this.slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
        val stack = slot.item.takeIf { it.count > 0 } ?: return ItemStack.EMPTY

        val copy = stack.copy()
        val moved =
            when (slotIndex) {
                0 -> moveItemStackTo(stack, 1, 36, true)
                in 1..9 -> moveItemStackTo(stack, 0, 1, false) || moveItemStackTo(stack, 10, 36, false)
                in 10..36 -> moveItemStackTo(stack, 0, 1, false) || moveItemStackTo(stack, 1, 9, false)
                else -> moveItemStackTo(stack, 1, 36, false)
            }

        if (!moved) return ItemStack.EMPTY

        if (stack.isEmpty) {
            slot.set(ItemStack.EMPTY)
        } else {
            slot.setChanged()
        }

        slot.onTake(player, stack)
        return if (stack.count == copy.count) ItemStack.EMPTY else copy
    }

    override fun stillValid(player: Player): Boolean = stillValid(access, player, BlockRegistry.rayTower.get()) && isAssembled()

    private fun isAssembled(): Boolean = (blockEntity as? AssembledMultiblockPartEntity)?.isAssembledMultiblock == true
}
