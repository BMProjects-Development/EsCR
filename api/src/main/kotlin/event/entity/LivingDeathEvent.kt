package com.algorithmlx.ecr.api.event.entity

import com.algorithmlx.ecr.api.event.engine.ICancellableEvent
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity

class LivingDeathEvent(
    override val entity: LivingEntity,
    val source: DamageSource
) : LivingEvent(entity), ICancellableEvent
