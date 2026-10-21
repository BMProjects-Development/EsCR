package com.algorithmlx.ecr.common.api

import com.algorithmlx.ecr.api.item.SoulStoneLike
import com.algorithmlx.ecr.common.components.SoulStoneComponent
import com.algorithmlx.ecr.registry.DataComponentRegistry
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack

object SoulStoneHelper {
    @JvmStatic
    fun setActualName(player: Player, stack: ItemStack) {
        if (stack.item !is SoulStoneLike) return
        val component = stack[DataComponentRegistry.soulStone.get()] ?: return

        if (component == SoulStoneComponent.EMPTY) return
        if (player.stringUUID != component.owner.toString() || player.name.string == component.ownerName) return

        stack[DataComponentRegistry.soulStone.get()] = component.copy(
            ownerName = player.name.string
        )
    }

    @JvmStatic
    fun isOwner(player: Player, stack: ItemStack): Boolean {
        if (stack.item !is SoulStoneLike) return false

        val component = stack[DataComponentRegistry.soulStone.get()] ?: return false

        return component.owner.toString() == player.stringUUID
    }

    @JvmStatic
    fun isOwnerOnline(level: ServerLevel, stack: ItemStack): Boolean {
        if (stack.item !is SoulStoneLike) return false

        val component = stack[DataComponentRegistry.soulStone.get()] ?: return false
        return component == SoulStoneComponent.EMPTY || level.server.playerList.getPlayer(component.owner) != null
    }

}
