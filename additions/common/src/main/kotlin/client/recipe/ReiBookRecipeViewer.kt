package com.algorithmlx.ecr.additions.client.recipe

import com.algorithmlx.ecr.api.client.research.BookRecipeViewer
import me.shedaniel.rei.api.client.view.ViewSearchBuilder
import me.shedaniel.rei.api.common.util.EntryStacks
import net.minecraft.world.item.ItemStack

object ReiBookRecipeViewer : BookRecipeViewer {
    override fun openRecipes(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return ViewSearchBuilder.builder().addRecipesFor(EntryStacks.of(stack)).open()
    }
}
