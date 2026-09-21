package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.container.AbstractMenu
import com.algorithmlx.ecr.api.container.slot.VanillaSpecialSlot
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.menu.MenuTypeData
import com.algorithmlx.ecr.common.api.BoundGemHelper
import com.algorithmlx.ecr.common.block.entity.RadiatingChamberEntity
import com.algorithmlx.ecr.registry.BlockRegistry
import com.algorithmlx.ecr.registry.MenuTypeRegistry
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

class RadiatingChamberMenu(
    containerId: Int,
    inv: Inventory,
    container: Container,
    val blockEntity: BlockEntity?,
    access: ContainerLevelAccess
) : AbstractMenu(MenuTypeRegistry.radiatingChamber, containerId, access) {
    constructor(containerId: Int, inventory: Inventory, typeData: MenuTypeData) : this(
        containerId,
        inventory,
        SimpleContainer(MACHINE_SLOTS),
        inventory.player.level().getBlockEntity(typeData.pos),
        ContainerLevelAccess.NULL
    )

    init {
        checkContainerSize(container, MACHINE_SLOTS)

        addSlot(VanillaSpecialSlot(container, RadiatingChamberEntity.BOUND_GEM_SLOT, 152, 57, BoundGemHelper::isConnectionFoundSpecial))
        addSlot(VanillaSpecialSlot(container, RadiatingChamberEntity.INPUT_SLOT, 8, 31))
        addSlot(VanillaSpecialSlot(container, RadiatingChamberEntity.SECONDARY_SLOT, 8, 57))
        addSlot(VanillaSpecialSlot(container, RadiatingChamberEntity.OUTPUT_SLOT, 44, 44, { false }))

        inv.make()
    }

    override fun quickMoveStack(
        player: Player,
        index: Int
    ): ItemStack {
        val slot = this.slots.getOrNull(index) ?: return ItemStack.EMPTY
        if (!slot.hasItem()) return ItemStack.EMPTY

        val stack = slot.item
        val copy = stack.copy()
        val moved = when {
            index < MACHINE_SLOTS -> this.moveItemStackTo(stack, MACHINE_SLOTS, PLAYER_SLOTS_END, true)
            stack.item is BoundGem -> this.moveItemStackTo(stack, 0, 1, false)
            else -> this.moveItemStackTo(stack, 1, 3, false)
        }

        if (!moved) {
            if (index in MACHINE_SLOTS ..< HOTBAR_START) {
                if (!this.moveItemStackTo(stack, HOTBAR_START, PLAYER_SLOTS_END, false)) return ItemStack.EMPTY
            } else if (index in HOTBAR_START..<PLAYER_SLOTS_END) {
                if (!this.moveItemStackTo(stack, MACHINE_SLOTS, HOTBAR_START, false)) return ItemStack.EMPTY
            } else return ItemStack.EMPTY
        }

        if (stack.isEmpty) slot.set(ItemStack.EMPTY)
        else slot.setChanged()

        if (stack.count == copy.count) return ItemStack.EMPTY

        slot.onQuickCraft(stack, copy)
        slot.onTake(player, stack)
        return copy
    }

    override fun stillValid(player: Player): Boolean = stillValid(this.access, player, BlockRegistry.radiatingChamber.get())

    companion object {
        const val MACHINE_SLOTS = 4
        private const val HOTBAR_START = MACHINE_SLOTS + 27
        private const val PLAYER_SLOTS_END = HOTBAR_START + 9
    }
}
