package com.algorithmlx.ecr.api.event.entity.player

import com.algorithmlx.ecr.api.event.engine.ICancellableEvent
import com.algorithmlx.ecr.api.event.entity.PlayerEvent
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player

class AttackEntityEvent(override val entity: Player, val target: Entity): PlayerEvent(entity), ICancellableEvent
