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
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

class MagicalTeleporterMenu(
    containerId: Int,
    inv: Inventory,
    container: Container,
    val blockEntity: BlockEntity?,
    access: ContainerLevelAccess
): AbstractMenu(
    MenuTypeRegistry.magicalTeleporter, containerId,
    access
) {
    constructor(containerId: Int, inv: Inventory, data: MenuTypeData): this(
        containerId, inv, SimpleContainer(2), inv.player.level().getBlockEntity(data.pos), ContainerLevelAccess.NULL
    )

    init {
        inv.make()

        addSlot(VanillaSpecialSlot(container, 0, 44, 48, BoundGemHelper::isConnectionFoundSpecial))
        addSlot(VanillaSpecialSlot(container, 1, 116, 48, BoundGemHelper::isConnectionFoundSpecial))
    }

    override fun quickMoveStack(
        player: Player,
        slotIndex: Int
    ): ItemStack = ItemStack.EMPTY // later

    override fun stillValid(player: Player): Boolean = stillValid(access, player, BlockRegistry.magicalTeleporter.get())
}
