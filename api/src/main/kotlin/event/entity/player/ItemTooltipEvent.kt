package com.algorithmlx.ecr.api.event.entity.player

import com.algorithmlx.ecr.api.event.entity.PlayerEvent
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item.TooltipContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.component.TooltipDisplay

class ItemTooltipEvent(
    val itemStack: ItemStack,
    entity: Player?,
    val toolTip: MutableList<Component>,
    val flags: TooltipFlag,
    val context: TooltipContext,
    val display: TooltipDisplay
): PlayerEvent(entity)
