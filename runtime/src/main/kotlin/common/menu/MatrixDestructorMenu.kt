package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.container.AbstractMenu
import com.algorithmlx.ecr.api.container.slot.VanillaSpecialSlot
import com.algorithmlx.ecr.api.menu.MenuTypeData
import com.algorithmlx.ecr.common.components.SoulStoneComponent
import com.algorithmlx.ecr.registry.BlockRegistry
import com.algorithmlx.ecr.registry.DataComponentRegistry
import com.algorithmlx.ecr.registry.MenuTypeRegistry
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

class MatrixDestructorMenu(
    containerId: Int,
    inv: Inventory,
    container: Container,
    val blockEntity: BlockEntity?,
    access: ContainerLevelAccess
) : AbstractMenu(MenuTypeRegistry.matrixDestructor, containerId, access) {
    constructor(containerId: Int, inv: Inventory, typeData: MenuTypeData) : this(
        containerId,
        inv,
        SimpleContainer(1),
        inv.player.level().getBlockEntity(typeData.pos),
        ContainerLevelAccess.NULL
    )

    init {
        addSlot(
            VanillaSpecialSlot(
                container,
                0,
                80,
                60,
                {
                    val component = it.get(DataComponentRegistry.soulStone.get())
                    component != null && component != SoulStoneComponent.EMPTY
                }
            )
        )

        inv.make()
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

            if (index == 0) {
                if (!this.moveItemStackTo(raw, 1, 37, true)) {
                    return ItemStack.EMPTY
                }
            } else if (index in 1..<37) {
                if (!this.moveItemStackTo(raw, 0, 1, false)) {
                    if (index in 11..<37) {
                        if (!this.moveItemStackTo(raw, 1, 10, false)) {
                            return ItemStack.EMPTY
                        }
                    } else if (!this.moveItemStackTo(raw, 11, 37, false)) {
                        return ItemStack.EMPTY
                    }
                }
            } else if (!this.moveItemStackTo(raw, 1, 37, false)) {
                return ItemStack.EMPTY
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

    override fun stillValid(player: Player): Boolean = stillValid(this.access, player, BlockRegistry.matrixDestructor.get())
}
