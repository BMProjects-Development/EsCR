package com.algorithmlx.ecr.init

import com.algorithmlx.ecr.api.utils.ecRL
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey

object ResourceKeys {
    @JvmField val MRU_DAMAGE_TYPE = registerDamageType(ECRModIDs.MRU)

    @JvmField val MAGIC_BREAK = registerEnchantments(ECRModIDs.MAGIC_BREAK)

    private fun registerDamageType(name: String) = ResourceKey.create(Registries.DAMAGE_TYPE, name.ecRL)
    private fun registerEnchantments(id: String) = ResourceKey.create(Registries.ENCHANTMENT, id.ecRL)
}
