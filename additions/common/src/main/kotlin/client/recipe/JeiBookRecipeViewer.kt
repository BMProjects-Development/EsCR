package com.algorithmlx.ecr.additions.client.recipe

import com.algorithmlx.ecr.api.client.research.BookRecipeViewer
import mezz.jei.api.constants.VanillaTypes
import mezz.jei.api.recipe.RecipeIngredientRole
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.client.Minecraft
import net.minecraft.world.item.ItemStack

object JeiBookRecipeViewer : BookRecipeViewer {
    @Volatile
    private var runtime: IJeiRuntime? = null

    fun attach(runtime: IJeiRuntime) {
        this.runtime = runtime
    }

    fun detach() {
        runtime = null
    }

    override fun openRecipes(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        val runtime = runtime ?: return false
        val minecraft = Minecraft.getInstance()
        val previous = minecraft.gui.screen()
        val focus = runtime.jeiHelpers.focusFactory.createFocus(RecipeIngredientRole.OUTPUT, VanillaTypes.ITEM_STACK, stack)
        runtime.recipesGui.show(focus)
        return minecraft.gui.screen() !== previous
    }
}
