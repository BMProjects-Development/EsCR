package com.algorithmlx.ecr.api.recipe

import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeInput
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.level.Level

class CachedRecipe<I: RecipeInput, T: Recipe<I>>(private val type: RecipeType<T>) {
    var recipeHolder: RecipeHolder<T>? = null

    fun test(input: I, level: ServerLevel): Boolean {
        if (isPresent() && recipeHolder!!.value().matches(input, level)) return true

        recipeHolder = level.recipeAccess().getRecipeFor(type, input, level).orElse(null)

        return recipeHolder != null
    }

    fun test(input: I, level: Level): Boolean = level is ServerLevel && test(input, level)

    fun testAndGet(input: I, level: ServerLevel) = if (test(input, level)) get()!! else null

    fun testAndGet(input: I, level: Level) = if (test(input, level)) get()!! else null

    fun isPresent() = this.recipeHolder != null

    fun get(): T? = recipeHolder?.value()

    fun identifier(): Identifier? = recipeHolder?.id?.identifier()
}
