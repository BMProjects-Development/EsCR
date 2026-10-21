package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.mim.MIMCraftingPattern
import com.algorithmlx.ecr.api.mim.MIMFrameProvider
import com.algorithmlx.ecr.registry.DataComponentRegistry
import net.minecraft.core.component.DataComponents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingInput

internal object VanillaCraftingFrame : MIMFrameProvider {
    override fun patterns(level: ServerLevel, frame: ItemStack): List<MIMCraftingPattern> {
        val encoded = frame[DataComponentRegistry.craftingFrame.get()] ?: return emptyList()
        val recipe = encoded.recipe(level)?.value() ?: return emptyList()
        val input = encoded.input()
        val output = recipe.assemble(input)
        if (output.isEmpty || input.isEmpty) return emptyList()
        val ingredients = recipe.placementInfo().ingredients()
        return listOf(MIMCraftingPattern(
            encoded to recipe, output, input.items(), { slot, candidate ->
                when {
                    ItemStack.isSameItemSameComponents(input.getItem(slot), candidate) -> true
                    !encoded.substituteIngredients -> false
                    ingredients.isNotEmpty() -> ingredients.any { it.test(input.getItem(slot)) && it.test(candidate) }
                    else -> {
                        val grid = input.items().toMutableList()
                        grid[slot] = candidate.copyWithCount(1)
                        val substituted = CraftingInput.of(input.width(), input.height(), grid)
                        recipe.matches(substituted, level) && ItemStack.matches(recipe.assemble(substituted), output)
                    }
                }
            }, { grid ->
                val actual = CraftingInput.of(input.width(), input.height(), grid)
                if (!recipe.matches(actual, level)) null
                else {
                    val result = recipe.assemble(actual)
                    if (!ItemStack.matches(result, output)) null
                    else listOf(result.copy()) + recipe.getRemainingItems(actual).filterNot(ItemStack::isEmpty).map(ItemStack::copy)
                }
            },
            if (output.has(DataComponents.MAP_POST_PROCESSING)) setOf(0) else emptySet(),
            { outputs -> outputs.also { it.first().onCraftedBySystem(level) } },
            byproducts = if (encoded.substituteIngredients) emptyList() else recipe.getRemainingItems(input).filterNot(ItemStack::isEmpty)
        ))
    }
}
