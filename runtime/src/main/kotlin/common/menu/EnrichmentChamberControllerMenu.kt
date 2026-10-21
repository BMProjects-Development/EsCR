package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.container.AbstractMenu
import com.algorithmlx.ecr.api.menu.MenuTypeData
import com.algorithmlx.ecr.registry.BlockRegistry
import com.algorithmlx.ecr.registry.MenuTypeRegistry
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

class EnrichmentChamberControllerMenu(
    containerId: Int,
    inv: Inventory,
    access: ContainerLevelAccess,
    val blockEntity: BlockEntity?
): AbstractMenu(MenuTypeRegistry.enrichmentChamberController, containerId, access) {
    constructor(containerId: Int, inventory: Inventory, typeData: MenuTypeData): this(
        containerId, inventory, ContainerLevelAccess.NULL,
        inventory.player.level().getBlockEntity(typeData.pos)
    )

    init {
        inv.make()
    }

    override fun quickMoveStack(
        player: Player,
        slotIndex: Int
    ): ItemStack {
        val slot = this.slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
        val stack = slot.item.takeIf { it.count > 0 } ?: return ItemStack.EMPTY

        val copy = stack.copy()
        val moved = when (slotIndex) {
            in 0 .. 8 -> moveItemStackTo(stack, 9, 35, false)
            in 9 .. 35 -> moveItemStackTo(stack, 0, 8, false)
            else -> moveItemStackTo(stack, 0, 35, false)
        }

        if (!moved) return ItemStack.EMPTY

        if (stack.isEmpty) slot.set(ItemStack.EMPTY)
        else slot.setChanged()

        slot.onTake(player, stack)
        return if (stack.count == copy.count) ItemStack.EMPTY else copy
    }

    override fun stillValid(player: Player): Boolean =
        stillValid(access, player, BlockRegistry.enrichmentChamberController.get())
}
