package com.algorithmlx.ecr.common.magic

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack

data class MagicDefenseContext(
    val target: Entity,
    val attacker: ServerPlayer,
    val directAttacker: Entity?,
    val source: DamageSource,
    val weapon: ItemStack,
    val originalDamage: Float,
    val enchantmentLevel: Int,
    val protectionScale: Float,
    val hasIgnore: Boolean
)

data class MagicDefenseResult(
    val damage: Float,
    val blocked: Boolean = false,
    val stopped: Boolean = false,
    val forceSuccessfulRoll: Boolean = false,
    val ignoreIncrease: Boolean = false
) {
    fun stop(damage: Float): MagicDefenseResult = copy(damage = damage, blocked = damage <= 0F, stopped = true)

    fun continueWith(damage: Float): MagicDefenseResult = copy(damage = damage, blocked = damage <= 0F)
}
