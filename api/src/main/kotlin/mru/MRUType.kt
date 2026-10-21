package com.algorithmlx.ecr.api.mru

import com.algorithmlx.ecr.api.registries.ECRegistries
import net.minecraft.network.chat.Component

open class MRUType(val toConvert: MRUType? = null, val consumeConvert: Int = -1) {
    val name: Component get() {
        val key = ECRegistries.MRU_TYPE.getKey(this) ?: throw NullPointerException("MRU Type is not registered")
        return Component.translatable("mru_type.${key.namespace}.${key.path}")
    }

    val isConvertable = this.toConvert != null && consumeConvert > 0
}
