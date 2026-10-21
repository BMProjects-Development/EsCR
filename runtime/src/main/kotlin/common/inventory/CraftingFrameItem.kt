package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.common.menu.CraftingFrameMenu
import com.algorithmlx.ecr.api.mim.MIMFrameProvider
import net.minecraft.server.level.ServerLevel
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

class CraftingFrameItem(properties: Properties) : Item(properties), MIMFrameProvider {
    override fun patterns(level: ServerLevel, frame: ItemStack) = VanillaCraftingFrame.patterns(level, frame)
    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (player is ServerPlayer) {
            val frameSlot = if (hand == InteractionHand.MAIN_HAND) player.inventory.selectedSlot else Inventory.SLOT_OFFHAND
            player.openMenu(SimpleMenuProvider(
                { id, inventory, _ -> CraftingFrameMenu(id, inventory, frameSlot) },
                Component.translatable("item.escr.crafting_frame")
            ))
        }
        return InteractionResult.SUCCESS
    }
}
