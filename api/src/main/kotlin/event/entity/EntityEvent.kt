package com.algorithmlx.ecr.api.event.entity

import com.algorithmlx.ecr.api.event.engine.Event
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

abstract class EntityEvent(open val entity: Entity?) : Event()

abstract class LivingEvent(open override val entity: LivingEntity?) : EntityEvent(entity)

abstract class PlayerEvent(open override val entity: Player?) : LivingEvent(entity)
