package com.algorithmlx.ecr.api.utils

import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient

object StackHelper {
    @JvmStatic
    fun areItemsEqual(stack1: ItemStack, stack2: ItemStack): Boolean =
        stack1.isEmpty && stack2.isEmpty || !stack1.isEmpty && ItemStack.isSameItem(stack1, stack2)

    @JvmStatic
    fun areStacksEqual(stack1: ItemStack, stack2: ItemStack): Boolean =
        areItemsEqual(stack1, stack2) && ItemStack.isSameItemSameComponents(stack1, stack2)

    @JvmStatic
    fun canCombineStacks(stack1: ItemStack, stack2: ItemStack): Boolean =
        !stack1.isEmpty && stack2.isEmpty || areStacksEqual(stack1, stack2) && (stack1.count + stack2.count) <= stack1.maxStackSize

    @JvmStatic
    fun canCombine(result: ItemStack, hand: ItemStack, count: Int, ingredientCount: Int): Boolean =
        canCombineStacks(result, hand) && count >= ingredientCount
}

var countByIngredient: (Ingredient) -> Int = { 1 }

val Ingredient.count get() = countByIngredient(this)
