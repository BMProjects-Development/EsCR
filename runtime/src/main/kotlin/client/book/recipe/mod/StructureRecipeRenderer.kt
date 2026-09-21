package com.algorithmlx.ecr.client.book.recipe.mod

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.client.research.BookRecipeRenderBuilder
import com.algorithmlx.ecr.api.client.research.BookRecipeRenderer
import com.algorithmlx.ecr.api.client.research.BookRecipeSlotType
import com.algorithmlx.ecr.common.recipe.StructureRecipe
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import java.util.Locale

object StructureRecipeRenderer : BookRecipeRenderer<StructureRecipe> {
    override fun build(
        recipe: StructureRecipe,
        builder: BookRecipeRenderBuilder
    ) {
        val display = recipe.display().filterIsInstance<StructureRecipe.Display>().firstOrNull() ?: return
        val font = builder.mc.font

        val dropText = Component.translatable("screen.$ModId.research_book.recipe.structure.drop")
        renderCenteredText(builder, dropText, 0)

        var y = font.lineHeight + TEXT_GAP
        builder.slot(display.ingredient, BookRecipeSlotType.INPUT, centeredSlotX(builder), y)
        y += SLOT_SIZE + SECTION_GAP

        recipe.structureCenter?.let { center ->
            val atText = Component.translatable("screen.$ModId.research_book.recipe.structure.at")
            val centerStack = ItemStack(center)
            renderCenteredText(builder, atText, y)
            y += font.lineHeight + TEXT_GAP
            builder.item(centerStack, centeredItemX(builder), y)
            builder.tooltip(centerStack.itemName, centeredItemX(builder), y, ITEM_SIZE)
            y += ITEM_SIZE + SECTION_GAP
        }

        builder.slot(display.result(), BookRecipeSlotType.RESULT, centeredSlotX(builder), y)
        y += SLOT_SIZE + SECTION_GAP

        listOf(
            Component.translatable("tooltip.$ModId.during", recipe.time),
            Component.translatable("screen.$ModId.research_book.recipe.structure.consume", recipe.consumeStructure),
            Component.translatable("screen.$ModId.research_book.recipe.structure.chance", chancePercent(recipe.chance))
        ).forEach { text ->
            renderCenteredText(builder, text, y)
            y += font.lineHeight
        }

        builder.multiblock(
            recipe.multiblock,
            (builder.width - MULTIBLOCK_WIDTH) / 2,
            y + SECTION_GAP,
            MULTIBLOCK_WIDTH,
            MULTIBLOCK_HEIGHT
        )
    }

    override fun width(recipe: StructureRecipe): Int = RENDER_WIDTH

    override fun height(recipe: StructureRecipe): Int = if (recipe.structureCenter == null) RENDER_HEIGHT else RENDER_HEIGHT_WITH_CENTER

    private fun chancePercent(chance: StructureRecipe.Range): String {
        if (chance.isEmpty()) return "100%"
        if (chance.max <= 0) return "0%"

        val max = chance.max.toLong()
        val successfulValues = (max - chance.min.toLong()).coerceIn(0L, max)
        val percent = successfulValues.toDouble() * 100.0 / max
        if (percent % 1.0 == 0.0) return "${percent.toInt()}%"
        return String.format(Locale.ROOT, "%.2f%%", percent)
    }

    private fun renderCenteredText(
        builder: BookRecipeRenderBuilder,
        text: Component,
        y: Int
    ) {
        builder.text(text, centeredX(builder, text), y)
    }

    private fun centeredX(
        builder: BookRecipeRenderBuilder,
        text: Component
    ): Int = (builder.width - builder.mc.font.width(text)) / 2

    private fun centeredSlotX(builder: BookRecipeRenderBuilder): Int = (builder.width - SLOT_SIZE) / 2

    private fun centeredItemX(builder: BookRecipeRenderBuilder): Int = (builder.width - ITEM_SIZE) / 2

    private const val RENDER_WIDTH = 225
    private const val RENDER_HEIGHT = 192
    private const val RENDER_HEIGHT_WITH_CENTER = 224

    private const val SLOT_SIZE = 32
    private const val ITEM_SIZE = SLOT_SIZE / 2

    private const val TEXT_GAP = 2
    private const val SECTION_GAP = 3

    private const val MULTIBLOCK_WIDTH = 160
    private const val MULTIBLOCK_HEIGHT = 80
}
