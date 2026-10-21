package com.algorithmlx.ecr.api.mim

import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

fun interface MIMFilterProvider {
    fun matches(filter: ItemStack, candidate: ItemStack): Boolean
}

object MIMFilters {
    private class Adapter(val accepts: (ItemStack) -> Boolean, val provider: MIMFilterProvider)
    private val adapters = linkedMapOf<Identifier, Adapter>()

    @JvmStatic
    fun register(id: Identifier, accepts: (ItemStack) -> Boolean, provider: MIMFilterProvider) {
        check(adapters.putIfAbsent(id, Adapter(accepts, provider)) == null) { "Duplicate MIM filter adapter: $id" }
    }

    @JvmStatic
    fun provider(stack: ItemStack): MIMFilterProvider? = if (stack.isEmpty) null
    else stack.item as? MIMFilterProvider ?: adapters.values.firstOrNull { it.accepts(stack) }?.provider

    @JvmStatic
    fun matches(filter: ItemStack, candidate: ItemStack): Boolean =
        filter.isEmpty || provider(filter)?.matches(filter.copy(), candidate.copy()) == true
}
