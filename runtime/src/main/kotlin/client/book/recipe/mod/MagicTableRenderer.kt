package com.algorithmlx.ecr.client.book.recipe.mod

import com.algorithmlx.ecr.api.client.research.BookRecipeRenderBuilder
import com.algorithmlx.ecr.api.client.research.BookRecipeRenderer
import com.algorithmlx.ecr.api.client.research.BookRecipeSlotType
import com.algorithmlx.ecr.common.recipe.MagicTableRecipe
import com.algorithmlx.ecr.registry.BlockRegistry
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.display.SlotDisplay
import kotlin.jvm.optionals.getOrElse

object MagicTableRenderer : BookRecipeRenderer<MagicTableRecipe> {
    override fun build(
        recipe: MagicTableRecipe,
        builder: BookRecipeRenderBuilder
    ) {
        val display = recipe.display().filterIsInstance<MagicTableRecipe.Display>().firstOrNull() ?: return

        val table = ItemStack(BlockRegistry.magicTable.get())
        val centerX = builder.width / 2
        val centerY = CONTENT_HEIGHT / 2

        val tableX = centerX + SLOT_SIZE * 2 + ELEMENT_GAP
        val tableY = centerY + (SLOT_SIZE - ITEM_SIZE) / 2

        builder.slot(display.catalyst.getOrElse { SlotDisplay.Empty.INSTANCE }, BookRecipeSlotType.INPUT, centerX, centerY)

        val inputs =
            display.input.getOrElse {
                buildList {
                    repeat(4) {
                        add(SlotDisplay.Empty.INSTANCE)
                    }
                }
            }

        (0..1).forEach { y ->
            (0..1).forEach { x ->
                val index = y * 2 + x
                val currentDisplay = inputs[index]

                builder.slot(
                    currentDisplay,
                    BookRecipeSlotType.INPUT,
                    centerX + (x * 2 - 1) * SLOT_SIZE,
                    centerY + (y * 2 - 1) * SLOT_SIZE
                )
            }
        }

        builder.slot(display.result(), BookRecipeSlotType.RESULT, centerX + SLOT_SIZE * 3, centerY)

        builder.item(table, tableX, tableY)
        builder.tooltip(table.itemName, tableX, tableY, ITEM_SIZE)
    }

    override fun height(recipe: MagicTableRecipe): Int = CONTENT_HEIGHT

    private const val SLOT_SIZE = 32
    private const val ITEM_SIZE = SLOT_SIZE / 2

    private const val ELEMENT_GAP = 8
    private const val TEXT_GAP = ELEMENT_GAP / 2

    private const val CONTENT_HEIGHT = 96
}
