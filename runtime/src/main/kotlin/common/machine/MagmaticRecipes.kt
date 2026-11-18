package com.algorithmlx.ecr.common.machine

import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.api.alloy.MagicalAlloys
import com.algorithmlx.ecr.api.alloy.MagmaticRecipes as ApiMagmaticRecipes
import com.algorithmlx.ecr.api.alloy.MagmaticRecipe as ApiMagmaticRecipe
import com.algorithmlx.ecr.registry.DataComponentRegistry
import com.algorithmlx.ecr.registry.ItemRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.SingleRecipeInput

typealias MagmaticRecipe = ApiMagmaticRecipe

object MagmaticRecipes {
    fun register(id: Identifier, provider: (ServerLevel, ItemStack) -> MagmaticRecipe?) = ApiMagmaticRecipes.register(id, provider)

    fun isAlloy(input: ItemStack): Boolean = input.`is`(ItemRegistry.magicalAlloy.get()) || input.has(DataComponentRegistry.alloyContents.get()) || MagicalAlloys.findItem(input) != null

    fun find(level: ServerLevel, input: ItemStack): MagmaticRecipe? {
        if (input.isEmpty) return null
        ApiMagmaticRecipes.find(level, input)?.let { return it }
        input[DataComponentRegistry.alloyContents.get()]?.let { contents ->
            val result = contents.output()
            return if (result.isEmpty || result.count > result.maxStackSize) null else MagmaticRecipe(result, false)
        }
        if (input.`is`(ItemRegistry.magicalAlloy.get())) {
            val result = input[DataComponentRegistry.magmaticAlloy.get()]?.copyOne() ?: return null
            if (result.isEmpty || result.count > result.maxStackSize / 2) return null
            return MagmaticRecipe(result.copyWithCount(result.count * 2), false)
        }
        MagicalAlloys.findItem(input)?.let { return MagmaticRecipe(it.result(), false) }
        MagicalAlloys.find(input)?.let { return MagmaticRecipe(it.create(), true) }
        val override = ECConfig.current.magmatic.oreOverrides.firstOrNull { entry ->
            val id = Identifier.parse(entry.input.removePrefix("#"))
            if (entry.input.startsWith("#")) input.`is`(TagKey.create(Registries.ITEM, id)) else BuiltInRegistries.ITEM.getKey(input.item) == id
        }
        val isOre = input.typeHolder().tags().anyMatch {
            val id = it.location
            id.path == "ores" || id.path.startsWith("ores/") || id.namespace == "minecraft" && id.path.endsWith("_ores")
        }
        if (override == null && !isOre) return null
        val result = if (override != null) BuiltInRegistries.ITEM.getOptional(Identifier.parse(override.output)).map(::ItemStack).orElse(ItemStack.EMPTY)
        else level.recipeAccess().getRecipeFor(RecipeType.SMELTING, SingleRecipeInput(input), level).map { it.value().assemble(SingleRecipeInput(input)) }.orElse(ItemStack.EMPTY)
        if (result.isEmpty || result.count > result.maxStackSize / 2) return null
        val alloy = ItemStack(ItemRegistry.magicalAlloy.get(), override?.alloys ?: 1)
        alloy[DataComponentRegistry.magmaticAlloy.get()] = ItemContainerContents.fromItems(listOf(result))
        return MagmaticRecipe(alloy, true)
    }
}
