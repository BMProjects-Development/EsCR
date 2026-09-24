package com.algorithmlx.ecr.common.magic

import com.algorithmlx.ecr.api.item.MagicShieldBreaker
import com.algorithmlx.ecr.api.research.ResearchProgress
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.init.ResourceKeys
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.init.config.MagicDefenseFunction
import com.algorithmlx.ecr.network.MagicShieldNetwork
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.enchantment.EnchantmentHelper

object MagicDefense {
    @JvmStatic
    fun resolve(target: Entity, level: ServerLevel, source: DamageSource, amount: Float): MagicDefenseResult {
        val entry = ECConfig.current.magicDefense(target.type) ?: return MagicDefenseResult(amount)
        val attacker = source.entity as? ServerPlayer ?: return MagicDefenseResult(amount)

        if (ResearchProgress.has(attacker, ECRModIDs.MAGIC_DEFENSE.ecRL) || attacker.isCreative)
            return MagicDefenseResult(amount)

        val weapon = source.weaponItem ?: attacker.weaponItem
        val shieldBreaker = weapon.item as? MagicShieldBreaker

        if (shieldBreaker?.breakMagicShield(weapon, target, source) == true) return MagicDefenseResult(amount)

        val enchantmentLevel = enchantmentLevel(level, weapon)
        val functions = entry.functions.sortedBy(MagicDefenseFunction::priority)
        val hasIgnore = functions.any(MagicDefenseFunction::ignoreDamage)

        if (!hasIgnore && enchantmentLevel >= 3) return MagicDefenseResult(amount)

        val context = MagicDefenseContext(
            target,
            attacker,
            source.directEntity,
            source,
            weapon,
            amount,
            enchantmentLevel,
            protectionScale(enchantmentLevel),
            hasIgnore
        )

        val result = functions.fold(MagicDefenseResult(amount)) { current, function ->
            if (current.stopped) current
            else function.apply(context, current)
        }

        MagicShieldNetwork.show(level, target, source, result.blocked)

        return result
    }

    private fun enchantmentLevel(level: ServerLevel, weapon: ItemStack): Int {
        val enchantment = level.registryAccess()
            .lookupOrThrow(Registries.ENCHANTMENT)
            .getOrThrow(ResourceKeys.MAGIC_BREAK)

        return EnchantmentHelper.getItemEnchantmentLevel(enchantment, weapon).coerceIn(0, 3)
    }

    private fun protectionScale(enchantmentLevel: Int) = when (enchantmentLevel) {
        1 -> 0.6F
        2 -> 0.25F
        3 -> 0F
        else -> 1F
    }
}
