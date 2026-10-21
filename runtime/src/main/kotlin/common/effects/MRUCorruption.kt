package com.algorithmlx.ecr.common.effects

import com.algorithmlx.ecr.init.ResourceKeys
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffects
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import java.awt.Color

class MRUCorruption: MobEffect(MobEffectCategory.HARMFUL, Color.magenta.rgb) {
    override fun applyEffectTick(serverLevel: ServerLevel, mob: LivingEntity, amplification: Int): Boolean {
        if (!mob.level().isClientSide && mob is Player) {
            if (mob.hasEffect(MobEffects.REGENERATION)) mob.removeEffect(MobEffects.REGENERATION)
            val access = mob.level().registryAccess()
            return mob.hurtServer(
                mob.level() as ServerLevel,
                DamageSource(access.getOrThrow(ResourceKeys.MRU_DAMAGE_TYPE)),
                if (amplification > 0) 2.5F * amplification + amplification else 2.5F
            )
        }

        return false
    }
}
