package com.algorithmlx.ecr.api.alloy

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack

class MagmaticRecipe(result: ItemStack, val ore: Boolean) {
    private val output = result.copy()
    val result: ItemStack get() = output.copy()

    init { require(!output.isEmpty && output.count <= output.maxStackSize) { "Invalid magmatic recipe result" } }
}

internal data class MagmaticRecipeProviderEntry(val id: Identifier, val provider: (ServerLevel, ItemStack) -> MagmaticRecipe?)

object MagmaticRecipes {
    @JvmStatic
    fun register(id: Identifier, provider: (ServerLevel, ItemStack) -> MagmaticRecipe?) {
        MagicalAlloys.registerAll(emptyList(), listOf(MagmaticRecipeProviderEntry(id, provider)))
    }

    @JvmStatic
    fun find(level: ServerLevel, input: ItemStack): MagmaticRecipe? =
        MagicalAlloys.findRecipe(level, input)
}
