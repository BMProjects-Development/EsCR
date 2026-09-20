package com.algorithmlx.ecr.client.book.renderer

import com.algorithmlx.ecr.api.client.research.BookElementRenderContext
import com.algorithmlx.ecr.api.client.research.BookElementRenderers
import com.algorithmlx.ecr.api.client.research.BookRecipeRenderers
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import com.algorithmlx.ecr.api.research.ResearchIds
import com.algorithmlx.ecr.api.research.content.*
import com.algorithmlx.ecr.client.book.BookLinkedTextLayout
import com.algorithmlx.ecr.client.book.controller.MultiblockBookPreviewController
import com.algorithmlx.ecr.client.book.recipe.mod.MagicTableRenderer
import com.algorithmlx.ecr.client.book.recipe.mod.MithrilineFurnaceRenderer
import com.algorithmlx.ecr.client.book.recipe.mod.RadiatingChamberRenderer
import com.algorithmlx.ecr.client.book.recipe.mod.StructureRecipeRenderer
import com.algorithmlx.ecr.client.book.recipe.vanilla.CookingRecipeRenderer
import com.algorithmlx.ecr.client.book.recipe.vanilla.CraftingTableRecipeRenderer
import com.algorithmlx.ecr.client.book.recipe.vanilla.StonecutterRecipeRenderer
import com.algorithmlx.ecr.registry.RecipeTypeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.crafting.RecipeType

object BookDefaultRenderers {
    fun init() {
        BookElementRenderers.register(ResearchIds.TEXT, ::renderText)
        BookElementRenderers.register(ResearchIds.ITEM, ::renderItem)
        BookElementRenderers.register(ResearchIds.BLOCK, ::renderBlock)
        BookElementRenderers.register(ResearchIds.GROUP, BookGroupRenderer::render)
        BookElementRenderers.register(ResearchIds.MULTIBLOCK, ::renderMultiblock)
        BookElementRenderers.register(ResearchIds.BOOK_MULTIBLOCK, ::renderBookMultiblock)
        BookElementRenderers.register(ResearchIds.ASSEMBLED_MULTIBLOCK, ::renderAssembledMultiblock)
        BookElementRenderers.register(ResearchIds.RECIPE, BookRecipeElementRenderer::render)
        BookElementRenderers.register(ResearchIds.TASK_LIST, BookTaskRenderer::render)

        BookRecipeRenderers.register(RecipeType.CRAFTING, CraftingTableRecipeRenderer)
        BookRecipeRenderers.register(RecipeType.SMELTING, CookingRecipeRenderer.Smelting)
        BookRecipeRenderers.register(RecipeType.BLASTING, CookingRecipeRenderer.Blasting)
        BookRecipeRenderers.register(RecipeType.SMOKING, CookingRecipeRenderer.Smoking)
        BookRecipeRenderers.register(RecipeType.CAMPFIRE_COOKING, CookingRecipeRenderer.CampfireCooking)
        BookRecipeRenderers.register(RecipeType.STONECUTTING, StonecutterRecipeRenderer)

        BookRecipeRenderers.register(RecipeTypeRegistry.mithrilineFurnace.get(), MithrilineFurnaceRenderer)
        BookRecipeRenderers.register(RecipeTypeRegistry.structure.get(), StructureRecipeRenderer)
        BookRecipeRenderers.register(RecipeTypeRegistry.magicTable.get(), MagicTableRenderer)
        BookRecipeRenderers.register(RecipeTypeRegistry.radiatingChamber.get(), RadiatingChamberRenderer)
    }

    private fun renderText(
        context: BookElementRenderContext,
        element: TextBookElement,
    ) {
        BookLinkedTextLayout.render(context, element)
    }

    private fun renderItem(
        context: BookElementRenderContext,
        element: ItemBookElement,
    ) {
        val item = BuiltInRegistries.ITEM.getOptional(element.item).orElse(null) ?: return
        val stack = ItemStack(item, element.count)
        context.graphics.item(stack, context.x, context.y)
        if (element.count > 1) context.graphics.itemDecorations(Minecraft.getInstance().font, stack, context.x, context.y)
        if (element.tooltip) renderItemTooltip(context, stack)
    }

    private fun renderBlock(
        context: BookElementRenderContext,
        element: BlockBookElement,
    ) {
        val block = BuiltInRegistries.BLOCK.getOptional(element.block).orElse(null) ?: return
        context.graphics.item(ItemStack(block.asItem()), context.x, context.y)
    }

    private fun renderMultiblock(
        context: BookElementRenderContext,
        element: MultiblockBookElement,
    ) {
        val multiblock = MultiblockDefinitions[element.multiblock] ?: return
        MultiblockBookPreviewController.render(context, element, multiblock)
    }

    private fun renderBookMultiblock(
        context: BookElementRenderContext,
        element: BookMultiblockElement,
    ) {
        MultiblockBookPreviewController.render(
            context,
            MultiblockBookElement(
                ResearchIds.BOOK_MULTIBLOCK,
                element.scale,
                element.rotationX,
                element.rotationY,
                element.layer,
            ),
            element.multiblock,
        )
    }

    private fun renderAssembledMultiblock(
        context: BookElementRenderContext,
        element: AssembledMultiblockBookElement,
    ) {
        val multiblock = MultiblockDefinitions.assembled(element.multiblock) ?: return
        MultiblockBookPreviewController.render(context, element, multiblock)
    }

    private fun renderItemTooltip(
        context: BookElementRenderContext,
        stack: ItemStack,
    ) {
        val hoverWidth = minOf(16, context.width).coerceAtLeast(0)
        val hoverHeight = minOf(16, context.height).coerceAtLeast(0)
        if (context.mouseX !in context.x..<context.x + hoverWidth || context.mouseY !in context.y..<context.y + hoverHeight) return
        val minecraft = context.mc
        val level = minecraft.level ?: return
        val flag = if (minecraft.options.advancedItemTooltips) TooltipFlag.ADVANCED else TooltipFlag.NORMAL
        val lines = stack.getTooltipLines(Item.TooltipContext.of(level), minecraft.player, flag)
        val mouseX = context.screenX + ((context.mouseX - context.x) * context.scale).toInt()
        val mouseY = context.screenY + ((context.mouseY - context.y) * context.scale).toInt()
        context.graphics.setTooltipForNextFrame(minecraft.font, lines, stack.tooltipImage, mouseX, mouseY)
    }
}
