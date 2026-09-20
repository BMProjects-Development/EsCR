package com.algorithmlx.ecr.client.book.recipe.mod

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.client.research.BookRecipeRenderBuilder
import com.algorithmlx.ecr.api.client.research.BookRecipeRenderer
import com.algorithmlx.ecr.api.client.research.BookRecipeSlotType
import com.algorithmlx.ecr.common.recipe.MithrilineFurnaceRecipe
import com.algorithmlx.ecr.registry.BlockRegistry
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object MithrilineFurnaceRenderer : BookRecipeRenderer<MithrilineFurnaceRecipe> {
    override fun build(
        recipe: MithrilineFurnaceRecipe,
        builder: BookRecipeRenderBuilder,
    ) {
        val display = recipe.display().filterIsInstance<MithrilineFurnaceRecipe.Display>().firstOrNull() ?: return

        val startX = builder.width / 2
        val slotY = 0

        val furnace = ItemStack(BlockRegistry.mithrilineFurnace.get())
        val furnaceX = startX + SLOT_SIZE + ELEMENT_GAP
        val furnaceY = slotY + (SLOT_SIZE - ITEM_SIZE) / 2

        val resultX = furnaceX + ITEM_SIZE + ELEMENT_GAP

        val espeText =
            Component
                .empty()
                .append(recipe.espe.toString())
                .append(" ")
                .append(Component.translatable("screen.$ModId.research_book.recipe.espe"))
        val espeX = (builder.width) - builder.mc.font.width(espeText)

        builder.slot(display.ingredient, BookRecipeSlotType.INPUT, startX, slotY)

        builder.item(furnace, furnaceX, furnaceY)
        builder.tooltip(furnace.itemName, furnaceX, furnaceY, ITEM_SIZE)

        builder.slot(display.result(), BookRecipeSlotType.RESULT, resultX, slotY)

        builder.text(espeText, espeX, SLOT_SIZE + TEXT_GAP)
    }

    override fun width(recipe: MithrilineFurnaceRecipe): Int = RENDER_WIDTH

    override fun height(recipe: MithrilineFurnaceRecipe): Int = RENDER_HEIGHT

    private const val RENDER_WIDTH = 128
    private const val RENDER_HEIGHT = 48

    private const val SLOT_SIZE = 32
    private const val ITEM_SIZE = SLOT_SIZE / 2

    private const val ELEMENT_GAP = 8
    private const val TEXT_GAP = ELEMENT_GAP / 2
}
