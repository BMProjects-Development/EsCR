package com.algorithmlx.ecr.api.recipe.dsl

import net.minecraft.core.HolderLookup
import net.minecraft.resources.Identifier
import net.minecraft.world.item.crafting.AbstractCookingRecipe
import net.minecraft.world.item.crafting.BlastingRecipe
import net.minecraft.world.item.crafting.CampfireCookingRecipe
import net.minecraft.world.item.crafting.CookingBookCategory
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.ShapedRecipe
import net.minecraft.world.item.crafting.ShapedRecipePattern
import net.minecraft.world.item.crafting.ShapelessRecipe
import net.minecraft.world.item.crafting.SmeltingRecipe
import net.minecraft.world.item.crafting.SmithingTransformRecipe
import net.minecraft.world.item.crafting.SmokingRecipe
import net.minecraft.world.item.crafting.StonecutterRecipe
import java.util.Optional

data class ShapedRecipeSpec(
    override val id: Identifier,
    val pattern: List<String>,
    val keys: Map<Char, IngredientSpec>,
    val result: RecipeResult,
    val category: CraftingBookCategory,
    val group: String,
    val showNotification: Boolean
): RecipeSpec {
    override fun bake(registries: HolderLookup.Provider): RecipeHolder<*> {
        val resolved = keys.mapValues { (_, ingredient) -> ingredient.resolve(registries) }
        val recipe = ShapedRecipe(
            Recipe.CommonInfo(showNotification),
            CraftingRecipe.CraftingBookInfo(category, group),
            ShapedRecipePattern.of(resolved, pattern),
            result.template
        )
        return holder(id, recipe)
    }
}

data class ShapelessRecipeSpec(
    override val id: Identifier,
    val ingredients: List<IngredientSpec>,
    val result: RecipeResult,
    val category: CraftingBookCategory,
    val group: String,
    val showNotification: Boolean
) : RecipeSpec {
    override fun bake(registries: HolderLookup.Provider): RecipeHolder<*> {
        val recipe = ShapelessRecipe(
            Recipe.CommonInfo(showNotification),
            CraftingRecipe.CraftingBookInfo(category, group),
            result.template,
            ingredients.map { it.resolve(registries) }
        )
        return holder(id, recipe)
    }
}

data class CookingRecipeSpec(
    override val id: Identifier,
    val kind: Kind,
    val input: IngredientSpec,
    val result: RecipeResult,
    val experience: Float,
    val cookingTime: Int,
    val category: CookingBookCategory,
    val group: String,
    val showNotification: Boolean
) : RecipeSpec {
    override fun bake(registries: HolderLookup.Provider): RecipeHolder<*> {
        val common = Recipe.CommonInfo(showNotification)
        val book = AbstractCookingRecipe.CookingBookInfo(category, group)
        val inp = input.resolve(registries)

        val recipe = when (kind) {
            Kind.SMELTING -> SmeltingRecipe(common, book, inp, result.template, experience, cookingTime)
            Kind.BLASTING -> BlastingRecipe(common, book, inp, result.template, experience, cookingTime)
            Kind.SMOKING -> SmokingRecipe(common, book, inp, result.template, experience, cookingTime)
            Kind.CAMPFIRE -> CampfireCookingRecipe(common, book, inp, result.template, experience, cookingTime)
        }

        return holder(id, recipe)
    }

    enum class Kind {
        SMELTING, BLASTING, SMOKING, CAMPFIRE
    }
}

data class StonecutterRecipeSpec(
    override val id: Identifier,
    val input: IngredientSpec,
    val result: RecipeResult,
    val showNotification: Boolean
) : RecipeSpec {
    override fun bake(registries: HolderLookup.Provider): RecipeHolder<*> = holder(
        id,
        StonecutterRecipe(Recipe.CommonInfo(showNotification), input.resolve(registries), result.template)
    )
}

data class SmithingTransformRecipeSpec(
    override val id: Identifier,
    val template: IngredientSpec?,
    val base: IngredientSpec,
    val addition: IngredientSpec?,
    val result: RecipeResult,
    val showNotification: Boolean
) : RecipeSpec {
    override fun bake(registries: HolderLookup.Provider): RecipeHolder<*> = holder(
        id,
        SmithingTransformRecipe(
            Recipe.CommonInfo(showNotification),
            Optional.ofNullable(template?.resolve(registries)),
            base.resolve(registries),
            Optional.ofNullable(addition?.resolve(registries)),
            result.template
        )
    )
}
