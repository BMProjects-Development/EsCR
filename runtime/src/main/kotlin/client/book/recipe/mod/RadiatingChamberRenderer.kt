package com.algorithmlx.ecr.client.book.recipe.mod

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.client.research.BookRecipeRenderBuilder
import com.algorithmlx.ecr.api.client.research.BookRecipeRenderer
import com.algorithmlx.ecr.api.client.research.BookRecipeSlotType
import com.algorithmlx.ecr.common.recipe.RadiatingChamberRecipe
import com.algorithmlx.ecr.registry.BlockRegistry
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack

object RadiatingChamberRenderer: BookRecipeRenderer<RadiatingChamberRecipe> {
    override fun build(
        recipe: RadiatingChamberRecipe,
        builder: BookRecipeRenderBuilder,
    ) {
        val display = recipe.display().filterIsInstance<RadiatingChamberRecipe.Display>().firstOrNull() ?: return
        val contentWidth = SLOT_SIZE * 2 + ITEM_SIZE + ELEMENT_GAP * 2
        val startX = (builder.width - contentWidth) / 2
        val resultY = (SLOT_SIZE + TEXT_GAP) / 2
        val chamber = ItemStack(BlockRegistry.radiatingChamber.get())
        val chamberX = startX + SLOT_SIZE + ELEMENT_GAP
        val chamberY = resultY + (SLOT_SIZE - ITEM_SIZE) / 2

        builder.slot(display.input, BookRecipeSlotType.INPUT, startX, 0)
        builder.slot(display.secondary, BookRecipeSlotType.INPUT, startX, SLOT_SIZE + TEXT_GAP)
        builder.item(chamber, chamberX, chamberY)
        builder.tooltip(chamber.itemName, chamberX, chamberY, ITEM_SIZE)
        builder.slot(display.result(), BookRecipeSlotType.RESULT, chamberX + ITEM_SIZE + ELEMENT_GAP, resultY)

        val lines = listOf(
            Component.translatable("tooltip.$ModId.during", recipe.time),
            Component.translatable("tooltip.$ModId.radiating_chamber.mru_per_tick", recipe.mruPerTick),
            balanceText(recipe),
        )
        lines.forEachIndexed { index, text ->
            val textX = (builder.width - builder.mc.font.width(text)) / 2
            val textY = SLOT_SIZE * 2 + ELEMENT_GAP + index * (builder.mc.font.lineHeight + TEXT_GAP)
            builder.text(text, textX, textY)
        }
    }

    private fun balanceText(recipe: RadiatingChamberRecipe): Component {
        if (recipe.ignoreBalance) return Component.translatable("tooltip.$ModId.radiating_chamber.any_balance")
        val min = recipe.balance.min
        val max = recipe.balance.max
        val left = if (min.isPresent) "(" else "["
        val right = if (max.isPresent) ")" else "]"
        return Component.translatable(
            "tooltip.$ModId.radiating_chamber.balance",
            "$left${min.orElse(0.0)}, ${max.orElse(2.0)}$right",
        )
    }

    override fun width(recipe: RadiatingChamberRecipe): Int = 160

    override fun height(recipe: RadiatingChamberRecipe): Int = 112

    private const val SLOT_SIZE = 32
    private const val ITEM_SIZE = SLOT_SIZE / 2
    private const val ELEMENT_GAP = 8
    private const val TEXT_GAP = ELEMENT_GAP / 2
}
