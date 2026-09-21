package com.algorithmlx.ecr.client.book.recipe.vanilla

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.client.research.BookRecipeRenderBuilder
import com.algorithmlx.ecr.api.client.research.BookRecipeRenderer
import com.algorithmlx.ecr.api.client.research.BookRecipeSlotType
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.crafting.AbstractCookingRecipe
import net.minecraft.world.item.crafting.BlastingRecipe
import net.minecraft.world.item.crafting.CampfireCookingRecipe
import net.minecraft.world.item.crafting.SmeltingRecipe
import net.minecraft.world.item.crafting.SmokingRecipe
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay

abstract class CookingRecipeRenderer<T : AbstractCookingRecipe> : BookRecipeRenderer<T> {
    protected fun render(
        builder: BookRecipeRenderBuilder,
        ingredient: SlotDisplay,
        result: SlotDisplay,
        duration: Int,
        item: ItemStack
    ) {
        val contentWidth = SLOT_SIZE + ELEMENT_GAP + ITEM_SIZE + ELEMENT_GAP + SLOT_SIZE

        val startX = (builder.width - contentWidth) / 2
        val slotY = 0

        val furnaceX = startX + SLOT_SIZE + ELEMENT_GAP
        val furnaceY = slotY + (SLOT_SIZE - ITEM_SIZE) / 2

        val resultX = furnaceX + ITEM_SIZE + ELEMENT_GAP

        val durationText =
            Component.translatable(
                "screen.$ModId.research_book.recipe.duration",
                Component.translatable(
                    "screen.$ModId.research_book.recipe.duration.seconds",
                    duration / 20
                )
            )
        val durationX = (builder.width - builder.mc.font.width(durationText)) / 2

        builder.slot(ingredient, BookRecipeSlotType.INPUT, startX, slotY)

        builder.item(item, furnaceX, furnaceY)
        builder.tooltip(
            item.itemName,
            furnaceX,
            furnaceY,
            ITEM_SIZE
        )

        builder.slot(result, BookRecipeSlotType.RESULT, resultX, slotY)

        builder.text(durationText, durationX, SLOT_SIZE + TEXT_GAP)
    }

    override fun width(recipe: T): Int = RENDER_WIDTH

    override fun height(recipe: T): Int = RENDER_HEIGHT

    companion object {
        private const val RENDER_WIDTH = 128
        private const val RENDER_HEIGHT = 48

        private const val SLOT_SIZE = 32
        private const val ITEM_SIZE = SLOT_SIZE / 2

        private const val ELEMENT_GAP = 8
        private const val TEXT_GAP = ELEMENT_GAP / 2
    }

    object Smelting : CookingRecipeRenderer<SmeltingRecipe>() {
        override fun build(
            recipe: SmeltingRecipe,
            builder: BookRecipeRenderBuilder
        ) {
            val display = recipe.display().filterIsInstance<FurnaceRecipeDisplay>().firstOrNull() ?: return
            this.render(builder, display.ingredient(), display.result(), display.duration(), ItemStack(Items.FURNACE))
        }
    }

    object Blasting : CookingRecipeRenderer<BlastingRecipe>() {
        override fun build(
            recipe: BlastingRecipe,
            builder: BookRecipeRenderBuilder
        ) {
            val display = recipe.display().filterIsInstance<FurnaceRecipeDisplay>().firstOrNull() ?: return
            this.render(builder, display.ingredient(), display.result(), display.duration(), ItemStack(Items.BLAST_FURNACE))
        }
    }

    object Smoking : CookingRecipeRenderer<SmokingRecipe>() {
        override fun build(
            recipe: SmokingRecipe,
            builder: BookRecipeRenderBuilder
        ) {
            val display = recipe.display().filterIsInstance<FurnaceRecipeDisplay>().firstOrNull() ?: return
            this.render(builder, display.ingredient(), display.result(), display.duration(), ItemStack(Items.SMOKER))
        }
    }

    object CampfireCooking : CookingRecipeRenderer<CampfireCookingRecipe>() {
        override fun build(
            recipe: CampfireCookingRecipe,
            builder: BookRecipeRenderBuilder
        ) {
            val display = recipe.display().filterIsInstance<FurnaceRecipeDisplay>().firstOrNull() ?: return
            this.render(builder, display.ingredient(), display.result(), display.duration(), ItemStack(Items.CAMPFIRE))
        }
    }
}
