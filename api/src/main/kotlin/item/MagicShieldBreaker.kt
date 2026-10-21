package com.algorithmlx.ecr.api.item

import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack

interface MagicShieldBreaker {
    fun breakMagicShield(stack: ItemStack, target: Entity, source: DamageSource): Boolean = true
}
