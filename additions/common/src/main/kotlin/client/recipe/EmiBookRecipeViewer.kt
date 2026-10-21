package com.algorithmlx.ecr.additions.client.recipe

import com.algorithmlx.ecr.api.client.research.BookRecipeViewer
import dev.emi.emi.api.EmiApi
import dev.emi.emi.api.stack.EmiStack
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

object EmiBookRecipeViewer : BookRecipeViewer {
    override fun openRecipes(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val minecraft = Minecraft.getInstance()
        val previous = minecraft.gui.screen()
        EmiApi.displayRecipes(EmiStack.of(stack))
        return minecraft.gui.screen() !== previous
    }
}
