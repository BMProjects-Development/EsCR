package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.container.AbstractMenu
import com.algorithmlx.ecr.api.container.slot.VanillaSpecialSlot
import com.algorithmlx.ecr.api.menu.MenuTypeData
import com.algorithmlx.ecr.common.api.BoundGemHelper
import com.algorithmlx.ecr.registry.BlockRegistry
import com.algorithmlx.ecr.registry.MenuTypeRegistry
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

class MagicTableMenu(
    containerId: Int,
    inv: Inventory,
    container: Container,
    val blockEntity: BlockEntity?,
    access: ContainerLevelAccess,
    val data: ContainerData,
) : AbstractMenu(MenuTypeRegistry.magicTable, containerId, access) {
    constructor(containerId: Int, inventory: Inventory, typeData: MenuTypeData) : this(
        containerId,
        inventory,
        SimpleContainer(8),
        inventory.player.level().getBlockEntity(typeData.pos),
        ContainerLevelAccess.NULL,
        SimpleContainerData(2),
    )

    init {
        buildSlots(container, inv)

        addSlot(VanillaSpecialSlot(container, 7, 152, 16, { false }, { false }, isHighlightable = { !container.getItem(7).isEmpty }))
    }

    private fun buildSlots(
        container: Container,
        inv: Inventory,
        stackSize: Int = 64,
    ) {
        // Recipe slots
        addSlot(VanillaSpecialSlot(container, 0, 26, 17, stackSize = stackSize, place = { this.item.isEmpty }))
        addSlot(VanillaSpecialSlot(container, 1, 62, 17, stackSize = stackSize, place = { this.item.isEmpty }))
        addSlot(VanillaSpecialSlot(container, 2, 26, 53, stackSize = stackSize, place = { this.item.isEmpty }))
        addSlot(VanillaSpecialSlot(container, 3, 62, 53, stackSize = stackSize, place = { this.item.isEmpty }))

        // Catalyst
        addSlot(VanillaSpecialSlot(container, 4, 44, 35, stackSize = stackSize, stackSizeWithItem = { stackSize }))
        // Result
        addSlot(VanillaSpecialSlot(container, 5, 116, 35, { false }))

        // Bound gem
        addSlot(
            VanillaSpecialSlot(
                container,
                6,
                152,
                53,
                BoundGemHelper::isConnectionFoundSpecial,
            ),
        )

        inv.make()

        addDataSlots(data)
    }

    override fun quickMoveStack(
        player: Player,
        slotIndex: Int,
    ): ItemStack {
        val slot = this.slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
        val stack = slot.item.takeIf { it.count > 0 } ?: return ItemStack.EMPTY

        val copy = stack.copy()
        val moved =
            when (slotIndex) {
                0 -> moveItemStackTo(stack, 7, 43, true)
                in 7..<17 -> moveItemStackTo(stack, 0, 6, false) || moveItemStackTo(stack, 17, 43, false)
                in 17..<43 -> moveItemStackTo(stack, 0, 6, false) || moveItemStackTo(stack, 7, 16, false)
                else -> moveItemStackTo(stack, 7, 43, false)
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

    override fun stillValid(player: Player): Boolean = stillValid(this.access, player, BlockRegistry.magicTable.get())
}
