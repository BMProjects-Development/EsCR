package com.algorithmlx.ecr.client.book.recipe.vanilla

import com.algorithmlx.ecr.api.client.research.BookRecipeRenderBuilder
import com.algorithmlx.ecr.api.client.research.BookRecipeRenderer
import com.algorithmlx.ecr.api.client.research.BookRecipeSlotType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.StonecutterRecipe
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay

object StonecutterRecipeRenderer : BookRecipeRenderer<StonecutterRecipe> {
    override fun build(
        recipe: StonecutterRecipe,
        builder: BookRecipeRenderBuilder
    ) {
        val display = recipe.display().filterIsInstance<StonecutterRecipeDisplay>().firstOrNull() ?: return

        val item = ItemStack(Items.STONECUTTER)

        val width = SLOT_SIZE + ELEMENT_GAP + ITEM_SIZE + ELEMENT_GAP + SLOT_SIZE

        val startX = (builder.width - width) / 2
        val slotY = 0

        val stonecutterX = startX + SLOT_SIZE + ELEMENT_GAP
        val stonecutterY = slotY + (SLOT_SIZE - ITEM_SIZE) / 2

        val resultX = stonecutterX + ITEM_SIZE + ELEMENT_GAP

        builder.slot(display.input(), BookRecipeSlotType.INPUT, startX, slotY)

        builder.item(item, stonecutterX, stonecutterY)
        builder.tooltip(item.itemName, stonecutterX, stonecutterY, ITEM_SIZE)

        builder.slot(display.result(), BookRecipeSlotType.RESULT, resultX, slotY)
    }

    override fun width(recipe: StonecutterRecipe): Int = RENDER_WIDTH

    override fun height(recipe: StonecutterRecipe): Int = RENDER_HEIGHT

    private const val RENDER_WIDTH = 128
    private const val RENDER_HEIGHT = 32

    private const val SLOT_SIZE = 32
    private const val ITEM_SIZE = SLOT_SIZE / 2

    private const val ELEMENT_GAP = 8
}
