package com.algorithmlx.ecr.api.event.tick

import com.algorithmlx.ecr.api.event.entity.PlayerEvent
import net.minecraft.world.entity.player.Player

sealed class PlayerTickEvent(final override val entity: Player) : PlayerEvent(entity) {
    class Pre(entity: Player) : PlayerTickEvent(entity)
    class Post(entity: Player) : PlayerTickEvent(entity)
}
