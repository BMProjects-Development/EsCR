package com.algorithmlx.ecr.api.client.research

import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDefinition
import com.algorithmlx.ecr.api.client.drawMRULine
import com.algorithmlx.ecr.api.mru.storage.MRUStorage
import com.algorithmlx.ecr.api.multiblock.Multiblock
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import com.algorithmlx.ecr.api.research.content.BookResearchLink
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.display.SlotDisplay
import net.minecraft.world.level.ItemLike
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

enum class BookRecipeSlotType {
    INPUT,
    RESULT
}

sealed interface BookRecipeRenderElement {
    fun render(context: BookElementRenderContext)
}

sealed interface BookRecipeSlotContent {
    data class Stacks(
        val stacks: List<ItemStack>
    ) : BookRecipeSlotContent

    data class Display(
        val display: SlotDisplay
    ) : BookRecipeSlotContent
}

class BookRecipeSlot internal constructor(
    val content: BookRecipeSlotContent,
    val slotType: BookRecipeSlotType,
    val x: Int,
    val y: Int
) : BookRecipeRenderElement {
    private val tooltipAdditions = mutableListOf<Component>()
    val additions: List<Component> get() = tooltipAdditions

    fun withAddition(addition: Component): BookRecipeSlot =
        apply {
            tooltipAdditions += addition
        }

    fun withAddition(addition: String): BookRecipeSlot = withAddition(Component.translatable(addition))

    override fun render(context: BookElementRenderContext): Unit = throw AssertionError("Used default render")
}

data class BookRecipeSprite(
    val sprite: Identifier,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
) : BookRecipeRenderElement {
    override fun render(context: BookElementRenderContext) {
        context.graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            this.sprite,
            context.x + this.x,
            context.y + this.y,
            this.width,
            this.height
        )
    }
}

data class BookRecipeItemSprite(
    val item: ItemStack,
    val x: Int,
    val y: Int
) : BookRecipeRenderElement {
    override fun render(context: BookElementRenderContext) {
        context.graphics.item(item, context.x + this.x, context.y + this.y)
        if (item.count > 1) {
            context.graphics.itemDecorations(context.mc.font, this.item, context.x + this.x, context.y + this.y)
        }
    }
}

data class BookRecipeTooltip(
    val text: Component,
    val x: Int,
    val y: Int,
    val size: Int
) : BookRecipeRenderElement {
    override fun render(context: BookElementRenderContext): Unit = throw AssertionError("Used default render")
}

data class BookRecipeText(
    val text: Component,
    val x: Int,
    val y: Int,
    val color: Int,
    val shadow: Boolean
) : BookRecipeRenderElement {
    override fun render(context: BookElementRenderContext) {
        context.graphics.text(
            context.mc.font,
            this.text,
            context.x + this.x,
            context.y + this.y,
            this.color,
            this.shadow
        )
    }
}

data class BookRecipeLink(
    val text: Component,
    val target: BookResearchLink,
    val x: Int,
    val y: Int,
    val color: Int,
    val hoverColor: Int,
    val shadow: Boolean
) : BookRecipeRenderElement {
    override fun render(context: BookElementRenderContext) {
        val font = context.mc.font
        val color = this.color
        context.graphics.text(font, this.text, context.x + this.x, context.y + this.y, color, this.shadow)
        val underlineY = context.y + this.y + font.lineHeight - 1
        context.graphics.fill(
            context.x + this.x,
            underlineY,
            context.x + this.x + font.width(this.text),
            underlineY + 1,
            color
        )
    }
}

data class BookRecipeMRULine(
    val mruStorage: MRUStorage,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val color: Int,
    val hoverColor: Int
) : BookRecipeRenderElement {
    override fun render(context: BookElementRenderContext) {
        drawMRULine(
            context.graphics,
            mruStorage,
            x,
            y,
            context.x,
            context.y,
            width,
            height,
            context.mouseX,
            context.mouseY,
            colorIn = color,
            colorOut = hoverColor
        )
    }
}

data class BookRecipeMultiblock(
    val multiblock: Identifier,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val scale: Float,
    val rotationX: Float,
    val rotationY: Float,
    val layer: Int
) : BookRecipeRenderElement {
    init {
        require(width > 0 && height > 0)
    }

    override fun render(context: BookElementRenderContext): Unit = throw AssertionError("Used default render")
}

data class BookRecipeAssembledMultiblock(
    val multiblock: Identifier,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val assembled: Boolean,
    val scale: Float,
    val rotationX: Float,
    val rotationY: Float,
    val layer: Int
) : BookRecipeRenderElement {
    init {
        require(width > 0 && height > 0)
    }

    override fun render(context: BookElementRenderContext): Unit = throw AssertionError("Used default render")
}

class BookRecipeRenderBuilder private constructor(
    private val renderContext: BookElementRenderContext?,
    val width: Int,
    val research: Identifier?
) {
    constructor(context: BookElementRenderContext) : this(context, context.width, context.research)

    val context: BookElementRenderContext
        get() = renderContext ?: throw IllegalStateException(MEASURE_CONTEXT_ERROR)

    val mc: Minecraft get() = Minecraft.getInstance()
    val elements: List<BookRecipeRenderElement> field = mutableListOf<BookRecipeRenderElement>()
    var contentHeight: Int = 0
        private set

    private fun include(
        y: Int,
        height: Int
    ) {
        contentHeight = max(contentHeight, y + height)
    }

    fun slot(
        stack: ItemStack,
        slotType: BookRecipeSlotType,
        x: Int,
        y: Int
    ): BookRecipeSlot = slot(listOf(stack), slotType, x, y)

    fun slot(
        item: ItemLike,
        slotType: BookRecipeSlotType,
        x: Int,
        y: Int
    ): BookRecipeSlot = slot(ItemStack(item), slotType, x, y)

    fun slot(
        stacks: List<ItemStack>,
        slotType: BookRecipeSlotType,
        x: Int,
        y: Int
    ): BookRecipeSlot {
        val slot =
            BookRecipeSlot(
                BookRecipeSlotContent.Stacks(stacks.filterNot(ItemStack::isEmpty).map(ItemStack::copy)),
                slotType,
                x,
                y
            )
        include(y, SLOT_SIZE)
        elements += slot
        return slot
    }

    fun slot(
        display: SlotDisplay,
        slotType: BookRecipeSlotType,
        x: Int,
        y: Int
    ): BookRecipeSlot {
        val slot = BookRecipeSlot(BookRecipeSlotContent.Display(display), slotType, x, y)
        include(y, SLOT_SIZE)
        elements += slot
        return slot
    }

    fun sprite(
        sprite: Identifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): BookRecipeSprite =
        BookRecipeSprite(sprite, x, y, width, height).also {
            include(y, height)
            elements += it
        }

    fun item(
        item: ItemStack,
        x: Int,
        y: Int
    ): BookRecipeItemSprite =
        BookRecipeItemSprite(item, x, y).also {
            include(y, ITEM_SIZE)
            elements += it
        }

    fun tooltip(
        text: Component,
        x: Int,
        y: Int,
        size: Int
    ): BookRecipeTooltip =
        BookRecipeTooltip(text, x, y, size).also {
            include(y, size)
            elements += it
        }

    fun tooltip(
        text: String,
        x: Int,
        y: Int,
        size: Int
    ) = tooltip(Component.translatable(text), x, y, size)

    @JvmOverloads
    fun text(
        text: Component,
        x: Int,
        y: Int,
        color: Int = 0xFF202020.toInt(),
        shadow: Boolean = false
    ): BookRecipeText =
        BookRecipeText(text, x, y, color, shadow).also {
            include(y, mc.font.lineHeight)
            elements += it
        }

    @JvmOverloads
    fun text(
        text: String,
        x: Int,
        y: Int,
        color: Int = 0xFF202020.toInt(),
        shadow: Boolean = false
    ): BookRecipeText = text(Component.literal(text), x, y, color, shadow)

    @JvmOverloads
    fun link(
        text: Component,
        target: BookResearchLink,
        x: Int,
        y: Int,
        color: Int = 0xFF2F67B1.toInt(),
        hoverColor: Int = 0xFF1B4F91.toInt(),
        shadow: Boolean = false
    ): BookRecipeLink =
        BookRecipeLink(text, target, x, y, color, hoverColor, shadow).also {
            include(y, mc.font.lineHeight)
            elements += it
        }

    @JvmOverloads
    fun link(
        text: Component,
        target: String,
        x: Int,
        y: Int,
        color: Int = 0xFF2F67B1.toInt(),
        hoverColor: Int = 0xFF1B4F91.toInt(),
        shadow: Boolean = false
    ): BookRecipeLink =
        link(
            text,
            requireNotNull(BookResearchLink.parse(target, research)) { "Invalid research link: $target" },
            x,
            y,
            color,
            hoverColor,
            shadow
        )

    @JvmOverloads
    fun link(
        text: String,
        target: BookResearchLink,
        x: Int,
        y: Int,
        color: Int = 0xFF2F67B1.toInt(),
        hoverColor: Int = 0xFF1B4F91.toInt(),
        shadow: Boolean = false
    ): BookRecipeLink = link(Component.literal(text), target, x, y, color, hoverColor, shadow)

    @JvmOverloads
    fun link(
        text: String,
        target: String,
        x: Int,
        y: Int,
        color: Int = 0xFF2F67B1.toInt(),
        hoverColor: Int = 0xFF1B4F91.toInt(),
        shadow: Boolean = false
    ): BookRecipeLink = link(Component.literal(text), target, x, y, color, hoverColor, shadow)

    fun multiblock(
        multiblock: Identifier,
        width: Int,
        height: Int
    ): BookRecipeMultiblock = multiblock(multiblock, 0, 0, width, height)

    fun multiblock(
        multiblock: String,
        width: Int,
        height: Int
    ): BookRecipeMultiblock = multiblock(Identifier.parse(multiblock), width, height)

    fun multiblock(
        multiblock: Multiblock,
        width: Int,
        height: Int
    ): BookRecipeMultiblock = multiblock(multiblock.id(), width, height)

    @JvmOverloads
    fun multiblock(
        multiblock: Identifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        scale: Float = 0.9F,
        rotationX: Float = 25F,
        rotationY: Float = -30F,
        layer: Int = Int.MAX_VALUE
    ): BookRecipeMultiblock =
        BookRecipeMultiblock(
            multiblock,
            x,
            y,
            width,
            height,
            scale,
            rotationX,
            rotationY,
            layer
        ).also {
            include(y, height)
            elements += it
        }

    @JvmOverloads
    fun multiblock(
        multiblock: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        scale: Float = 0.9F,
        rotationX: Float = 25F,
        rotationY: Float = -30F,
        layer: Int = Int.MAX_VALUE
    ): BookRecipeMultiblock =
        multiblock(
            Identifier.parse(multiblock),
            x,
            y,
            width,
            height,
            scale,
            rotationX,
            rotationY,
            layer
        )

    @JvmOverloads
    fun multiblock(
        multiblock: Multiblock,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        scale: Float = 0.9F,
        rotationX: Float = 25F,
        rotationY: Float = -30F,
        layer: Int = Int.MAX_VALUE
    ): BookRecipeMultiblock =
        multiblock(
            multiblock.id(),
            x,
            y,
            width,
            height,
            scale,
            rotationX,
            rotationY,
            layer
        )

    fun assembledMultiblock(
        multiblock: Identifier,
        width: Int,
        height: Int
    ): BookRecipeAssembledMultiblock = assembledMultiblock(multiblock, 0, 0, width, height)

    fun assembledMultiblock(
        multiblock: String,
        width: Int,
        height: Int
    ): BookRecipeAssembledMultiblock = assembledMultiblock(Identifier.parse(multiblock), width, height)

    fun assembledMultiblock(
        multiblock: AssembledMultiblockDefinition,
        width: Int,
        height: Int
    ): BookRecipeAssembledMultiblock = assembledMultiblock(multiblock.id, width, height)

    @JvmOverloads
    fun assembledMultiblock(
        multiblock: Identifier,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        assembled: Boolean = false,
        scale: Float = 0.9F,
        rotationX: Float = 25F,
        rotationY: Float = -30F,
        layer: Int = Int.MAX_VALUE
    ): BookRecipeAssembledMultiblock =
        BookRecipeAssembledMultiblock(
            multiblock,
            x,
            y,
            width,
            height,
            assembled,
            scale,
            rotationX,
            rotationY,
            layer
        ).also {
            include(y, height)
            elements += it
        }

    @JvmOverloads
    fun assembledMultiblock(
        multiblock: String,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        assembled: Boolean = false,
        scale: Float = 0.9F,
        rotationX: Float = 25F,
        rotationY: Float = -30F,
        layer: Int = Int.MAX_VALUE
    ): BookRecipeAssembledMultiblock =
        assembledMultiblock(
            Identifier.parse(multiblock),
            x,
            y,
            width,
            height,
            assembled,
            scale,
            rotationX,
            rotationY,
            layer
        )

    @JvmOverloads
    fun assembledMultiblock(
        multiblock: AssembledMultiblockDefinition,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        assembled: Boolean = false,
        scale: Float = 0.9F,
        rotationX: Float = 25F,
        rotationY: Float = -30F,
        layer: Int = Int.MAX_VALUE
    ): BookRecipeAssembledMultiblock =
        assembledMultiblock(
            multiblock.id,
            x,
            y,
            width,
            height,
            assembled,
            scale,
            rotationX,
            rotationY,
            layer
        )

    @JvmOverloads
    fun mruLine(
        mruStorage: MRUStorage,
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        color: Int = 0x8B00FF,
        hoverColor: Int = 0x32127A
    ): BookRecipeMRULine = BookRecipeMRULine(mruStorage, x, y, width, height, color, hoverColor)

    private fun Multiblock.id(): Identifier = requireNotNull(MultiblockDefinitions.id(this)) { "Multiblock is not registered" }

    companion object {
        private const val SLOT_SIZE = 32
        private const val ITEM_SIZE = 16
        private const val MEASURE_CONTEXT_ERROR = "Render context is not available while measuring a recipe"

        fun measure(
            width: Int,
            research: Identifier?
        ): BookRecipeRenderBuilder = BookRecipeRenderBuilder(null, width, research)

        fun isMeasureContextError(error: IllegalStateException): Boolean = error.message == MEASURE_CONTEXT_ERROR
    }
}

fun interface BookRecipeRenderer<T : Recipe<*>> {
    fun build(
        recipe: T,
        builder: BookRecipeRenderBuilder
    )

    fun width(recipe: T): Int = 160

    fun height(recipe: T): Int = 96
}

object BookRecipeRenderers {
    private val recipeRenderers = ConcurrentHashMap<Identifier, BookRecipeRenderer<out Recipe<*>>>()
    private val typeRenderers = ConcurrentHashMap<RecipeType<*>, BookRecipeRenderer<out Recipe<*>>>()

    @JvmStatic
    fun <T : Recipe<*>> register(
        recipe: Identifier,
        renderer: BookRecipeRenderer<T>
    ) {
        check(recipeRenderers.putIfAbsent(recipe, renderer) == null) { "Duplicate recipe renderer: $recipe" }
    }

    @JvmStatic
    fun <T : Recipe<*>> register(
        type: RecipeType<T>,
        renderer: BookRecipeRenderer<T>
    ) {
        check(typeRenderers.putIfAbsent(type, renderer) == null) { "Duplicate recipe type renderer: $type" }
    }

    @Suppress("UNCHECKED_CAST")
    private fun renderer(
        recipeId: Identifier,
        recipe: Recipe<*>
    ) = (
        recipeRenderers[recipeId]
            ?: typeRenderers[recipe.type]
    ) as? BookRecipeRenderer<Recipe<*>>

    fun build(
        recipeId: Identifier,
        recipe: Recipe<*>,
        context: BookElementRenderContext
    ): BookRecipeRenderBuilder? {
        val renderer = renderer(recipeId, recipe) ?: return null
        return BookRecipeRenderBuilder(context).also { renderer.build(recipe, it) }
    }

    fun measureHeight(
        recipeId: Identifier,
        recipe: Recipe<*>,
        width: Int,
        research: Identifier?
    ): Int? {
        val renderer = renderer(recipeId, recipe) ?: return null
        return try {
            BookRecipeRenderBuilder
                .measure(width, research)
                .also { renderer.build(recipe, it) }
                .contentHeight
        } catch (error: IllegalStateException) {
            if (BookRecipeRenderBuilder.isMeasureContextError(error)) null else throw error
        }
    }

    fun width(
        recipeId: Identifier,
        recipe: Recipe<*>
    ): Int? = renderer(recipeId, recipe)?.width(recipe)

    fun height(
        recipeId: Identifier,
        recipe: Recipe<*>
    ): Int? = renderer(recipeId, recipe)?.height(recipe)

    @JvmStatic
    fun hasRenderer(
        recipeId: Identifier,
        recipe: Recipe<*>
    ): Boolean = recipeRenderers.containsKey(recipeId) || typeRenderers.containsKey(recipe.type)
}
