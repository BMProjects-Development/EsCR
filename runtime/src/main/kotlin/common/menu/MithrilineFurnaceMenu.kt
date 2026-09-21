package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.container.AbstractMenu
import com.algorithmlx.ecr.api.container.slot.VanillaSpecialSlot
import com.algorithmlx.ecr.api.menu.MenuTypeData
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

class MithrilineFurnaceMenu(
    containerId: Int,
    inv: Inventory,
    container: Container,
    val blockEntity: BlockEntity?,
    access: ContainerLevelAccess,
    val data: ContainerData
) : AbstractMenu(MenuTypeRegistry.mithrilineFurnace, containerId, access) {
    constructor(containerId: Int, inventory: Inventory, typeData: MenuTypeData) : this(
        containerId,
        inventory,
        SimpleContainer(2),
        inventory.player.level().getBlockEntity(typeData.pos),
        ContainerLevelAccess.NULL,
        SimpleContainerData(2)
    )

    init {
        addSlot(VanillaSpecialSlot(container, 0, 80, 60))
        addSlot(VanillaSpecialSlot(container, 1, 80, 22, { false }))

        inv.make()

        addDataSlots(data)
    }

    override fun quickMoveStack(
        player: Player,
        index: Int
    ): ItemStack {
        var qms = ItemStack.EMPTY
        val ms = this.slots[index]

        if (ms.hasItem()) {
            val raw = ms.item

            qms = raw.copy()

            when (index) {
                0 -> {
                    if (!this.moveItemStackTo(raw, 2, 38, false)) {
                        return ItemStack.EMPTY
                    }

                    ms.onQuickCraft(raw, qms)
                }

                1 -> {
                    if (!this.moveItemStackTo(raw, 2, 38, true)) {
                        return ItemStack.EMPTY
                    }

                    ms.onQuickCraft(raw, qms)
                }

                in 2..<38 -> {
                    if (!this.moveItemStackTo(raw, 0, 1, false)) {
                        if (index in 12..<38) {
                            if (!this.moveItemStackTo(raw, 2, 11, false)) {
                                return ItemStack.EMPTY
                            }
                        } else if (!this.moveItemStackTo(raw, 12, 38, false)) {
                            return ItemStack.EMPTY
                        }
                    }
                }

                else -> {
                    if (!this.moveItemStackTo(raw, 2, 38, false)) {
                        return ItemStack.EMPTY
                    }
                }
            }

            if (raw.isEmpty) {
                ms.set(ItemStack.EMPTY)
            } else {
                ms.setChanged()
            }

            if (raw.count == qms.count) return ItemStack.EMPTY

            ms.onTake(player, raw)
        }

        return qms
    }

    override fun stillValid(player: Player): Boolean = stillValid(this.access, player, BlockRegistry.mithrilineFurnace.get())
}
