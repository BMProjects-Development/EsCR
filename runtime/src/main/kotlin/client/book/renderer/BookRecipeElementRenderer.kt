package com.algorithmlx.ecr.client.book.renderer

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.client.research.*
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import com.algorithmlx.ecr.api.research.ClientResearchState
import com.algorithmlx.ecr.api.research.content.AssembledMultiblockBookElement
import com.algorithmlx.ecr.api.research.content.CraftingBookElement
import com.algorithmlx.ecr.api.research.content.MultiblockBookElement
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.client.book.BookResearchLinkController
import com.algorithmlx.ecr.client.book.controller.MultiblockBookPreviewController
import com.mojang.blaze3d.platform.cursor.CursorTypes
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Util
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.display.SlotDisplayContext
import kotlin.math.max
import kotlin.math.roundToInt

object BookRecipeElementRenderer {
    private val frameTexture = "textures/gui/book/frame.png".ecRL
    private var hoveredViewerStack = ItemStack.EMPTY

    fun render(
        context: BookElementRenderContext,
        element: CraftingBookElement
    ) {
        val recipe = ClientResearchState.recipe(element.recipe) ?: return
        val render = BookRecipeRenderers.build(element.recipe, recipe, context)
        if (render == null) {
            renderMissingRenderer(context, recipe)
            return
        }
        if (!contentFits(context, render.contentHeight)) return
        render.elements.forEachIndexed { index, element -> renderElement(context, element, index) }
    }

    fun preferredWidth(element: CraftingBookElement): Int? {
        val recipe = ClientResearchState.recipe(element.recipe) ?: return null
        return BookRecipeRenderers.width(element.recipe, recipe)
    }

    fun preferredHeight(
        element: CraftingBookElement,
        width: Int,
        research: Identifier?
    ): Int? {
        val recipe = ClientResearchState.recipe(element.recipe) ?: return null

        val declaredHeight = BookRecipeRenderers.height(element.recipe, recipe)
        val measuredHeight = BookRecipeRenderers.measureHeight(element.recipe, recipe, width, research)
        listOfNotNull(declaredHeight, measuredHeight).maxOrNull()?.let { return it }

        if (BookRecipeRenderers.hasRenderer(element.recipe, recipe)) return null
        val font = Minecraft.getInstance().font
        val lines = font.split(missingRendererMessage(recipe, resultStack(recipe)), width.coerceAtLeast(1))
        return lines.size * font.lineHeight + FAILURE_CONTENT_GAP + SLOT_SIZE
    }

    fun clearHoveredViewerStack() {
        hoveredViewerStack = ItemStack.EMPTY
    }

    fun openHoveredRecipe(): Boolean = BookRecipeViewers.openRecipes(hoveredViewerStack)

    private fun renderMissingRenderer(
        context: BookElementRenderContext,
        recipe: Recipe<*>
    ) {
        val result = resultStack(recipe)
        val font = context.mc.font
        val lines = font.split(missingRendererMessage(recipe, result), context.width.coerceAtLeast(1))
        lines.forEachIndexed { index, line ->
            context.graphics.text(
                font,
                line,
                context.x + (context.width - font.width(line)) / 2,
                context.y + index * font.lineHeight,
                FAILURE_TEXT_COLOR,
                false
            )
        }
        if (result.isEmpty) return
        val slot =
            BookRecipeRenderBuilder(context).slot(
                result,
                BookRecipeSlotType.RESULT,
                (context.width - SLOT_SIZE) / 2,
                lines.size * font.lineHeight + FAILURE_CONTENT_GAP
            )
        renderSlot(context, slot, true)
    }

    private fun renderElement(
        context: BookElementRenderContext,
        element: BookRecipeRenderElement,
        index: Int
    ) {
        when (element) {
            is BookRecipeSlot -> renderSlot(context, element, false)
            is BookRecipeTooltip -> renderTooltip(context, element)
            is BookRecipeLink -> renderLink(context, element)
            is BookRecipeMultiblock -> renderMultiblock(context, element, index)
            is BookRecipeAssembledMultiblock -> renderAssembledMultiblock(context, element, index)
            else -> element.render(context)
        }
    }

    private fun contentFits(
        context: BookElementRenderContext,
        contentHeight: Int
    ): Boolean {
        val scissor = context.scissorArea ?: return true
        val screenHeight = max(1, (contentHeight * context.scale).roundToInt())
        return context.screenX >= scissor.left() &&
            context.screenY >= scissor.top() &&
            context.screenX + context.screenWidth <= scissor.right() &&
            context.screenY + screenHeight <= scissor.bottom()
    }

    private fun renderMultiblock(
        context: BookElementRenderContext,
        element: BookRecipeMultiblock,
        index: Int
    ) {
        val multiblock = MultiblockDefinitions[element.multiblock] ?: return

        val x = context.x + element.x
        val y = context.y + element.y
        val screenX = context.screenX + (element.x * context.scale).roundToInt()
        val screenY = context.screenY + (element.y * context.scale).roundToInt()
        val subContext =
            context.copy(
                x = x,
                y = y,
                width = element.width,
                height = element.height,
                screenX = screenX,
                screenY = screenY,
                screenWidth = max(1, (element.width * context.scale).roundToInt()),
                screenHeight = max(1, (element.height * context.scale).roundToInt()),
                textLines = null,
                interactionKey = context.interactionKey?.let { "$it|recipe_multiblock_$index" },
                textLineStart = 0,
                textLineCount = 0
            )
        MultiblockBookPreviewController.render(
            subContext,
            MultiblockBookElement(
                element.multiblock,
                element.scale,
                element.rotationX,
                element.rotationY,
                element.layer
            ),
            multiblock
        )
    }

    private fun renderAssembledMultiblock(
        context: BookElementRenderContext,
        element: BookRecipeAssembledMultiblock,
        index: Int
    ) {
        val multiblock = MultiblockDefinitions.assembled(element.multiblock) ?: return
        val subContext = previewContext(context, element.x, element.y, element.width, element.height, index, true)
        MultiblockBookPreviewController.render(
            subContext,
            AssembledMultiblockBookElement(
                element.multiblock,
                element.assembled,
                element.scale,
                element.rotationX,
                element.rotationY,
                element.layer
            ),
            multiblock
        )
    }

    private fun previewContext(
        context: BookElementRenderContext,
        offsetX: Int,
        offsetY: Int,
        width: Int,
        height: Int,
        index: Int,
        assembled: Boolean
    ): BookElementRenderContext =
        context.copy(
            x = context.x + offsetX,
            y = context.y + offsetY,
            width = width,
            height = height,
            screenX = context.screenX + (offsetX * context.scale).roundToInt(),
            screenY = context.screenY + (offsetY * context.scale).roundToInt(),
            screenWidth = max(1, (width * context.scale).roundToInt()),
            screenHeight = max(1, (height * context.scale).roundToInt()),
            textLines = null,
            interactionKey =
                context.interactionKey?.let {
                    "$it|recipe_${if (assembled) "assembled_" else ""}multiblock_$index"
                },
            textLineStart = 0,
            textLineCount = 0
        )

    private fun renderLink(
        context: BookElementRenderContext,
        link: BookRecipeLink
    ) {
        val font = context.mc.font
        val x = context.x + link.x
        val y = context.y + link.y
        val width = font.width(link.text)
        val hovered = BookResearchLinkController.hover(context, link.target, x, y, width, font.lineHeight)
        val color = if (hovered) link.hoverColor else link.color
        context.graphics.text(font, link.text, x, y, color, link.shadow)
        val underlineY = y + font.lineHeight - 1
        context.graphics.fill(x, underlineY, x + width, underlineY + 1, color)
    }

    private fun renderSlot(
        context: BookElementRenderContext,
        slot: BookRecipeSlot,
        viewerTarget: Boolean
    ) {
        val x = context.x + slot.x
        val y = context.y + slot.y
        context.graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            frameTexture,
            x,
            y,
            0f,
            0f,
            SLOT_SIZE,
            SLOT_SIZE,
            FRAME_TEXTURE_SIZE,
            FRAME_TEXTURE_SIZE,
            FRAME_TEXTURE_SIZE,
            FRAME_TEXTURE_SIZE
        )

        val stacks = resolveStacks(slot)
        if (stacks.isEmpty()) return
        val stack = stacks[((Util.getMillis() / STACK_CYCLE_TIME) % stacks.size).toInt()]
        context.graphics.item(stack, x + ITEM_OFFSET, y + ITEM_OFFSET)
        if (stack.count > 1) {
            context.graphics.itemDecorations(context.mc.font, stack, x + ITEM_OFFSET, y + ITEM_OFFSET)
        }
        if (context.mouseX !in x..<x + SLOT_SIZE || context.mouseY !in y..<y + SLOT_SIZE) return
        renderStackTooltip(context, stack, slot)
        if (viewerTarget) {
            hoveredViewerStack = stack.copy()
            context.graphics.requestCursor(CursorTypes.POINTING_HAND)
        }
    }

    private fun renderTooltip(
        context: BookElementRenderContext,
        tooltip: BookRecipeTooltip
    ) {
        val x = context.x + tooltip.x
        val y = context.y + tooltip.y
        val size = tooltip.size
        if (context.mouseX !in x..<x + size || context.mouseY !in y..<y + size) return
        this.renderTooltip(context, tooltip.text)
    }

    private fun renderTooltip(
        context: BookElementRenderContext,
        text: Component
    ) {
        val minecraft = context.mc
        val mouseX = context.screenX + ((context.mouseX - context.x) * context.scale).toInt()
        val mouseY = context.screenY + ((context.mouseY - context.y) * context.scale).toInt()
        context.graphics.setTooltipForNextFrame(minecraft.font, text, mouseX, mouseY)
    }

    private fun resolveStacks(slot: BookRecipeSlot): List<ItemStack> =
        when (val content = slot.content) {
            is BookRecipeSlotContent.Stacks -> {
                content.stacks
            }

            is BookRecipeSlotContent.Display -> {
                Minecraft
                    .getInstance()
                    .level
                    ?.let(SlotDisplayContext::fromLevel)
                    ?.let(content.display::resolveForStacks)
                    .orEmpty()
            }
        }

    private fun renderStackTooltip(
        context: BookElementRenderContext,
        stack: ItemStack,
        slot: BookRecipeSlot
    ) {
        val minecraft = context.mc
        val level = minecraft.level ?: return
        val flag = if (minecraft.options.advancedItemTooltips) TooltipFlag.ADVANCED else TooltipFlag.NORMAL
        val lines = stack.getTooltipLines(Item.TooltipContext.of(level), minecraft.player, flag) + slot.additions
        val mouseX = context.screenX + ((context.mouseX - context.x) * context.scale).toInt()
        val mouseY = context.screenY + ((context.mouseY - context.y) * context.scale).toInt()
        context.graphics.setTooltipForNextFrame(minecraft.font, lines, stack.tooltipImage, mouseX, mouseY)
    }

    private fun resultStack(recipe: Recipe<*>): ItemStack {
        val level = Minecraft.getInstance().level ?: return ItemStack.EMPTY
        val displayContext = SlotDisplayContext.fromLevel(level)
        return recipe
            .display()
            .asSequence()
            .map { it.result().resolveForFirstStack(displayContext) }
            .firstOrNull { !it.isEmpty }
            ?: ItemStack.EMPTY
    }

    private fun missingRendererMessage(
        recipe: Recipe<*>,
        result: ItemStack
    ): Component {
        val itemName = if (result.isEmpty) Component.literal("<unknown>") else result.hoverName
        val recipeType = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.type)?.toString() ?: recipe.type.toString()
        return Component.translatable("screen.$ModId.research_book.recipe.failed", itemName, recipeType)
    }

    private const val SLOT_SIZE = 32
    private const val FRAME_TEXTURE_SIZE = 32
    private const val ITEM_OFFSET = 8
    private const val STACK_CYCLE_TIME = 1000L
    private const val FAILURE_CONTENT_GAP = 4
    private const val FAILURE_TEXT_COLOR = 0xFF8F2525.toInt()
}
