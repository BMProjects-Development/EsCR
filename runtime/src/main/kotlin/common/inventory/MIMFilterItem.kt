package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.common.menu.MIMFilterMenu
import com.algorithmlx.ecr.api.mim.MIMFilterProvider
import com.algorithmlx.ecr.registry.DataComponentRegistry
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.level.Level

class MIMFilterItem(properties: Properties, val advanced: Boolean = false) : Item(properties), MIMFilterProvider {
    override fun matches(filter: ItemStack, candidate: ItemStack): Boolean =
        (filter[DataComponentRegistry.mimFilter.get()] ?: MIMFilterComponent.EMPTY).matches(candidate)
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (player is ServerPlayer) {
            val slot = if (hand == InteractionHand.MAIN_HAND) player.inventory.selectedSlot else Inventory.SLOT_OFFHAND
            player.openMenu(SimpleMenuProvider(
                { id, inventory, _ -> MIMFilterMenu(id, inventory, slot) },
                Component.translatable(if (advanced) "item.escr.advanced_magical_inventory_filter" else "item.escr.magical_inventory_filter")
            ))
        }
        return InteractionResult.SUCCESS
    }
}
