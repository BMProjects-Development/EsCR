package com.algorithmlx.ecr.client.book.controller

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDefinition
import com.algorithmlx.ecr.api.client.render.MultiblockPreviewGuiBridge
import com.algorithmlx.ecr.api.client.render.MultiblockPreviewModel
import com.algorithmlx.ecr.api.client.render.MultiblockPreviewRenderState
import com.algorithmlx.ecr.api.client.render.MultiblockPreviewTransform
import com.algorithmlx.ecr.api.client.render.MultiblockWorldPreview
import com.algorithmlx.ecr.api.client.research.BookElementRenderContext
import com.algorithmlx.ecr.api.multiblock.Multiblock
import com.algorithmlx.ecr.api.research.content.AssembledMultiblockBookElement
import com.algorithmlx.ecr.api.research.content.MultiblockBookElement
import com.algorithmlx.ecr.api.utils.ecRL
import com.mojang.blaze3d.platform.InputConstants
import com.mojang.blaze3d.platform.cursor.CursorTypes
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt

object MultiblockBookPreviewController {
    private val arrowLeft = "textures/gui/book/arrow_left.png".ecRL
    private val arrowLeftSelected = "textures/gui/book/arrow_left_selected.png".ecRL
    private val arrowRight = "textures/gui/book/arrow_right.png".ecRL
    private val arrowRightSelected = "textures/gui/book/arrow_right_selected.png".ecRL
    private val fullModeTexture = "textures/gui/book/multiblock.png".ecRL
    private val layeredModeTexture = "textures/gui/book/multiblock_layered.png".ecRL
    private val hammerTexture = "textures/item/hammer.png".ecRL

    private val states = linkedMapOf<String, PreviewState>()
    private var frame = 0L
    private var draggingKey: String? = null
    private var draggingButton: Int? = null

    fun beginFrame() {
        frame++
    }

    fun clear() {
        states.clear()
        draggingKey = null
        draggingButton = null
    }

    fun render(
        context: BookElementRenderContext,
        element: MultiblockBookElement,
        multiblock: Multiblock,
    ) {
        val key = context.interactionKey ?: return renderStatic(context, element, multiblock)
        val state = states.getOrPut(key) { PreviewState.from(element, multiblock) }
        state.hasAssemblyToggle = false
        state.canAssemble = false
        state.assembled = false
        state.worldMultiblock = multiblock
        renderInteractive(
            context,
            key,
            state,
            MultiblockPreviewModel.Pattern(multiblock),
            multiblock.ySize,
        )
    }

    fun render(
        context: BookElementRenderContext,
        element: AssembledMultiblockBookElement,
        multiblock: AssembledMultiblockDefinition,
    ) {
        val key = context.interactionKey ?: return renderStatic(context, element, multiblock)
        val state = states.getOrPut(key) { PreviewState.from(element, multiblock) }
        state.hasAssemblyToggle = true
        state.canAssemble = multiblock.formedModel != null
        state.worldMultiblock = null
        if (!state.canAssemble) state.assembled = false
        renderInteractive(
            context,
            key,
            state,
            MultiblockPreviewModel.Assembled(multiblock, state.assembled),
            multiblock.ySize,
        )
    }

    private fun renderInteractive(
        context: BookElementRenderContext,
        key: String,
        state: PreviewState,
        model: MultiblockPreviewModel,
        layerCount: Int,
    ) {
        state.lastSeenFrame = frame
        state.maxLayer = (layerCount - 1).coerceAtLeast(0)
        state.layer = state.layer.coerceIn(0, state.maxLayer)

        val scissor = currentScissor(context)
        val elementBounds =
            Rect(
                context.screenX,
                context.screenY,
                context.screenWidth.coerceAtLeast(1),
                context.screenHeight.coerceAtLeast(1),
            )
        if (!elementBounds.isInside(scissor)) {
            state.previewBounds = Rect.EMPTY
            state.leftButton = Rect.EMPTY
            state.modeButton = Rect.EMPTY
            state.rightButton = Rect.EMPTY
            state.worldButton = Rect.EMPTY
            state.assemblyButton = Rect.EMPTY
            return
        }

        val previewLocalHeight = (context.height - CONTROL_RESERVED_HEIGHT).coerceAtLeast(1)
        val previewScreenHeight =
            (previewLocalHeight * context.scale)
                .roundToInt()
                .coerceIn(1, context.screenHeight.coerceAtLeast(1))

        val previewBounds =
            Rect(
                context.screenX,
                context.screenY,
                context.screenWidth.coerceAtLeast(1),
                previewScreenHeight,
            )
        val previewFullyVisible = previewBounds.isInside(scissor)
        if (!previewFullyVisible) {
            state.previewBounds = Rect.EMPTY
            state.leftButton = Rect.EMPTY
            state.modeButton = Rect.EMPTY
            state.rightButton = Rect.EMPTY
            state.worldButton = Rect.EMPTY
            state.assemblyButton = Rect.EMPTY
            return
        }
        state.previewBounds = previewBounds

        val controls = controlLayout(context, state.worldMultiblock != null, state.hasAssemblyToggle)
        state.leftButton = controls.left.toScreen(context)
        state.modeButton = controls.mode.toScreen(context)
        state.rightButton = controls.right.toScreen(context)
        state.worldButton = controls.world.toScreen(context)
        state.assemblyButton = controls.assembly.toScreen(context)

        MultiblockPreviewGuiBridge.add(
            context.graphics,
            MultiblockPreviewRenderState(
                model,
                state.transform(),
                context.screenX,
                context.screenY,
                context.screenX + context.screenWidth,
                context.screenY + previewScreenHeight,
                scissor,
                key,
            ),
        )

        renderControls(context, state, layerCount, controls)
    }

    private fun renderStatic(
        context: BookElementRenderContext,
        element: MultiblockBookElement,
        multiblock: Multiblock,
    ) {
        val bounds =
            Rect(
                context.screenX,
                context.screenY,
                context.screenWidth.coerceAtLeast(1),
                context.screenHeight.coerceAtLeast(1),
            )
        val scissor = currentScissor(context)
        if (bounds.isInside(scissor)) {
            MultiblockPreviewGuiBridge.add(
                context.graphics,
                MultiblockPreviewRenderState(
                    multiblock,
                    MultiblockPreviewTransform(
                        scale = element.scale,
                        rotationX = element.rotationX,
                        rotationY = element.rotationY,
                        layer = element.layer,
                    ),
                    context.screenX,
                    context.screenY,
                    context.screenX + context.screenWidth,
                    context.screenY + context.screenHeight,
                    scissor,
                    context.interactionKey ?: "${element.multiblock}|${context.screenX},${context.screenY}",
                ),
            )
        }
    }

    private fun renderStatic(
        context: BookElementRenderContext,
        element: AssembledMultiblockBookElement,
        multiblock: AssembledMultiblockDefinition,
    ) {
        val bounds =
            Rect(
                context.screenX,
                context.screenY,
                context.screenWidth.coerceAtLeast(1),
                context.screenHeight.coerceAtLeast(1),
            )
        val scissor = currentScissor(context)
        if (!bounds.isInside(scissor)) return

        val assembled = element.assembled && multiblock.formedModel != null
        MultiblockPreviewGuiBridge.add(
            context.graphics,
            MultiblockPreviewRenderState(
                MultiblockPreviewModel.Assembled(multiblock, assembled),
                MultiblockPreviewTransform(
                    scale = element.scale,
                    rotationX = element.rotationX,
                    rotationY = element.rotationY,
                    layer = if (assembled) Int.MAX_VALUE else element.layer,
                ),
                context.screenX,
                context.screenY,
                context.screenX + context.screenWidth,
                context.screenY + context.screenHeight,
                scissor,
                context.interactionKey ?: "${element.multiblock}|${context.screenX},${context.screenY}|assembled",
            ),
        )
    }

    private fun renderControls(
        context: BookElementRenderContext,
        state: PreviewState,
        layerCount: Int,
        controls: Controls,
    ) {
        val leftHovered = controls.left.contains(context.mouseX, context.mouseY)
        val modeHovered = controls.mode.contains(context.mouseX, context.mouseY)
        val rightHovered = controls.right.contains(context.mouseX, context.mouseY)
        val worldHovered = state.worldMultiblock != null && controls.world.contains(context.mouseX, context.mouseY)
        val assemblyHovered =
            state.hasAssemblyToggle && state.canAssemble &&
                controls.assembly.contains(context.mouseX, context.mouseY)
        val buttonsHovered = leftHovered || modeHovered || rightHovered || worldHovered || assemblyHovered
        val previewHovered =
            context.mouseX in context.x until context.x + context.width &&
                context.mouseY in context.y until context.y + (context.height - CONTROL_RESERVED_HEIGHT).coerceAtLeast(1)

        if (buttonsHovered) {
            context.graphics.requestCursor(CursorTypes.POINTING_HAND)
        } else if (previewHovered) {
            val panning =
                draggingKey == context.interactionKey &&
                    (draggingButton == RIGHT_MOUSE_BUTTON || shiftDown())
            context.graphics.requestCursor(if (panning) CursorTypes.RESIZE_ALL else CursorTypes.CROSSHAIR)
        }

        val canUseLayers = !state.assembled
        val canGoLeft = canUseLayers && state.layered && state.layer > 0
        val canGoRight = canUseLayers && state.layered && state.layer < state.maxLayer
        renderArrowButton(
            context,
            controls.left,
            if (leftHovered && canGoLeft) arrowLeftSelected else arrowLeft,
            canGoLeft,
        )

        val modeTexture = if (state.layered) layeredModeTexture else fullModeTexture
        val modeTextureSize = if (state.layered) LAYERED_MODE_TEXTURE_SIZE else FULL_MODE_TEXTURE_SIZE
        context.graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            modeTexture,
            controls.mode.x,
            controls.mode.y,
            0f,
            0f,
            BUTTON_SIZE,
            BUTTON_SIZE,
            modeTextureSize,
            modeTextureSize,
            modeTextureSize,
            modeTextureSize,
        )

        renderArrowButton(
            context,
            controls.right,
            if (rightHovered && canGoRight) arrowRightSelected else arrowRight,
            canGoRight,
        )

        if (state.worldMultiblock != null) {
            renderWorldPreviewButton(context, controls.world, worldHovered, canPlaceWorldPreview())
        }

        if (state.hasAssemblyToggle) {
            renderAssemblyButton(context, controls.assembly, state, assemblyHovered)
        }

        if (worldHovered) {
            val minecraft = context.mc
            val mouseX = context.screenX + ((context.mouseX - context.x) * context.scale).roundToInt()
            val mouseY = context.screenY + ((context.mouseY - context.y) * context.scale).roundToInt()
            context.graphics.setTooltipForNextFrame(
                minecraft.font,
                Component.translatable("screen.$ModId.research_book.multiblock.place"),
                mouseX,
                mouseY,
            )
        }

        if (state.layered && canUseLayers) {
            val label = "${state.layer + 1}/${layerCount.coerceAtLeast(1)}"
            val font = Minecraft.getInstance().font
            val labelWidth = font.width(label) / 2f
            context.graphics.pose().pushMatrix()
            context.graphics.pose().translate(
                controls.mode.x + BUTTON_SIZE / 2f - labelWidth / 2f,
                controls.mode.y - 5f,
            )
            context.graphics.pose().scale(0.5f, 0.5f)
            context.graphics.text(font, label, 0, 0, 0xFF404040.toInt(), false)
            context.graphics.pose().popMatrix()
        }
    }

    private fun renderAssemblyButton(
        context: BookElementRenderContext,
        bounds: Rect,
        state: PreviewState,
        hovered: Boolean,
    ) {
        if (state.assembled || hovered) {
            val color = if (state.assembled) ASSEMBLED_BUTTON_COLOR else HOVERED_BUTTON_COLOR
            context.graphics.fill(bounds.x - 1, bounds.y - 1, bounds.x + bounds.width + 1, bounds.y + bounds.height + 1, color)
        }
        context.graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            hammerTexture,
            bounds.x,
            bounds.y,
            0f,
            0f,
            BUTTON_SIZE,
            BUTTON_SIZE,
            HAMMER_TEXTURE_SIZE,
            HAMMER_TEXTURE_SIZE,
            HAMMER_TEXTURE_SIZE,
            HAMMER_TEXTURE_SIZE,
            if (state.canAssemble) 0xFFFFFFFF.toInt() else DISABLED_ARROW_TINT,
        )
    }

    private fun renderWorldPreviewButton(
        context: BookElementRenderContext,
        bounds: Rect,
        hovered: Boolean,
        enabled: Boolean,
    ) {
        if (hovered) {
            context.graphics.fill(
                bounds.x - 1,
                bounds.y - 1,
                bounds.x + bounds.width + 1,
                bounds.y + bounds.height + 1,
                HOVERED_BUTTON_COLOR,
            )
        }
        val color = if (enabled) 0xFFD5F6FF.toInt() else DISABLED_ARROW_TINT
        val centerX = bounds.x + bounds.width / 2
        val centerY = bounds.y + bounds.height / 2
        context.graphics.fill(centerX, bounds.y + 1, centerX + 1, bounds.y + bounds.height - 1, color)
        context.graphics.fill(bounds.x + 1, centerY, bounds.x + bounds.width - 1, centerY + 1, color)
        context.graphics.outline(bounds.x + 2, bounds.y + 2, bounds.width - 4, bounds.height - 4, color)
    }

    private fun renderArrowButton(
        context: BookElementRenderContext,
        bounds: Rect,
        texture: Identifier,
        enabled: Boolean,
    ) {
        context.graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            texture,
            bounds.x,
            bounds.y,
            0f,
            0f,
            BUTTON_SIZE,
            BUTTON_SIZE,
            ARROW_TEXTURE_WIDTH,
            ARROW_TEXTURE_HEIGHT,
            ARROW_TEXTURE_WIDTH,
            ARROW_TEXTURE_HEIGHT,
            if (enabled) 0xFFFFFFFF.toInt() else DISABLED_ARROW_TINT,
        )
    }

    fun mouseClicked(
        mouseX: Int,
        mouseY: Int,
        button: Int,
        shift: Boolean,
        closeScreen: () -> Unit = {},
    ): Boolean {
        if (button != LEFT_MOUSE_BUTTON && button != RIGHT_MOUSE_BUTTON) return false
        val entry =
            visibleStates().lastOrNull { (_, state) ->
                (
                    button == LEFT_MOUSE_BUTTON && (
                        state.leftButton.contains(mouseX, mouseY, BUTTON_HIT_PADDING) ||
                            state.modeButton.contains(mouseX, mouseY, BUTTON_HIT_PADDING) ||
                            state.rightButton.contains(mouseX, mouseY, BUTTON_HIT_PADDING) ||
                            (state.worldMultiblock != null && state.worldButton.contains(mouseX, mouseY, BUTTON_HIT_PADDING)) ||
                            (state.hasAssemblyToggle && state.assemblyButton.contains(mouseX, mouseY, BUTTON_HIT_PADDING))
                    )
                ) || state.previewBounds.contains(mouseX, mouseY)
            } ?: return false

        val (key, state) = entry
        when {
            button == LEFT_MOUSE_BUTTON && state.leftButton.contains(mouseX, mouseY, BUTTON_HIT_PADDING) -> {
                if (!state.assembled && state.layered) state.layer = (state.layer - 1).coerceAtLeast(0)
            }

            button == LEFT_MOUSE_BUTTON && state.modeButton.contains(mouseX, mouseY, BUTTON_HIT_PADDING) -> {
                if (!state.assembled) state.layered = !state.layered
            }

            button == LEFT_MOUSE_BUTTON && state.rightButton.contains(mouseX, mouseY, BUTTON_HIT_PADDING) -> {
                if (!state.assembled && state.layered) state.layer = (state.layer + 1).coerceAtMost(state.maxLayer)
            }

            button == LEFT_MOUSE_BUTTON && state.worldMultiblock != null &&
                state.worldButton.contains(mouseX, mouseY, BUTTON_HIT_PADDING) -> {
                if (state.worldMultiblock?.let(::placeWorldPreview) == true) closeScreen()
            }

            button == LEFT_MOUSE_BUTTON && state.hasAssemblyToggle &&
                state.assemblyButton.contains(mouseX, mouseY, BUTTON_HIT_PADDING) -> {
                if (state.canAssemble) state.assembled = !state.assembled
            }

            state.previewBounds.contains(mouseX, mouseY) -> {
                state.dragMode = if (button == RIGHT_MOUSE_BUTTON || shift) DragMode.PAN else DragMode.ROTATE
                draggingKey = key
                draggingButton = button
            }
        }
        return true
    }

    private fun placeWorldPreview(multiblock: Multiblock): Boolean {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return false
        val player = minecraft.player ?: return false
        val hit = minecraft.hitResult as? BlockHitResult ?: return false
        if (hit.type != HitResult.Type.BLOCK) return false
        val pattern = multiblock.variants.firstOrNull() ?: return false
        val centerMatcher = pattern[pattern.center.x, pattern.center.y, pattern.center.z]
        val center =
            if (centerMatcher.matches(level.getBlockState(hit.blockPos))) {
                hit.blockPos
            } else {
                hit.blockPos.relative(hit.direction)
            }
        val direction = player.direction.takeIf { it.axis.isHorizontal } ?: net.minecraft.core.Direction.NORTH
        if (!MultiblockWorldPreview.place(multiblock, center, direction)) return false
        player.sendOverlayMessage(
            Component.translatable(
                "screen.$ModId.research_book.multiblock.placed",
                center.x,
                center.y,
                center.z,
            ),
        )
        return true
    }

    private fun canPlaceWorldPreview(): Boolean {
        val minecraft = Minecraft.getInstance()
        return minecraft.level != null && minecraft.player != null &&
            (minecraft.hitResult as? BlockHitResult)?.type == HitResult.Type.BLOCK
    }

    fun mouseDragged(
        dragX: Double,
        dragY: Double,
        shift: Boolean,
    ): Boolean {
        val state = draggingKey?.let(states::get) ?: return false
        val mode = if (draggingButton == RIGHT_MOUSE_BUTTON || shift) DragMode.PAN else state.dragMode
        when (mode) {
            DragMode.PAN -> {
                state.offsetX += dragX.toFloat()
                state.offsetY += dragY.toFloat()
            }

            DragMode.ROTATE -> {
                state.rotationX += dragX.toFloat() * ROTATION_SPEED
                state.rotationY =
                    (state.rotationY - dragY.toFloat() * ROTATION_SPEED)
                        .coerceIn(MIN_VERTICAL_ROTATION, MAX_VERTICAL_ROTATION)
            }
        }
        return true
    }

    fun mouseReleased(button: Int): Boolean {
        if (draggingKey == null || draggingButton != button) return false
        draggingKey = null
        draggingButton = null
        return true
    }

    fun mouseScrolled(
        mouseX: Int,
        mouseY: Int,
        scrollY: Double,
    ): Boolean {
        val state =
            visibleStates()
                .lastOrNull { (_, state) -> state.previewBounds.contains(mouseX, mouseY) }
                ?.value
                ?: return false
        val multiplier = exp(scrollY.toFloat() * ZOOM_STEP)
        state.scale = (state.scale * multiplier).coerceIn(MIN_SCALE, MAX_SCALE)
        return true
    }

    private fun visibleStates(): List<Map.Entry<String, PreviewState>> =
        states.entries
            .filter { it.value.lastSeenFrame == frame }

    private fun controlLayout(
        context: BookElementRenderContext,
        worldPreview: Boolean,
        assemblyToggle: Boolean,
    ): Controls {
        val buttonCount = 3 + (if (worldPreview) 1 else 0) + (if (assemblyToggle) 1 else 0)
        val totalWidth = BUTTON_SIZE * buttonCount + BUTTON_GAP * (buttonCount - 1)
        val startX = context.x + (context.width - totalWidth) / 2
        val y = context.y + context.height - BUTTON_SIZE - CONTROL_BOTTOM_MARGIN
        var nextIndex = 3
        val world =
            if (worldPreview) {
                Rect(startX + (BUTTON_SIZE + BUTTON_GAP) * nextIndex++, y, BUTTON_SIZE, BUTTON_SIZE)
            } else {
                Rect.EMPTY
            }
        return Controls(
            Rect(startX, y, BUTTON_SIZE, BUTTON_SIZE),
            Rect(startX + BUTTON_SIZE + BUTTON_GAP, y, BUTTON_SIZE, BUTTON_SIZE),
            Rect(startX + (BUTTON_SIZE + BUTTON_GAP) * 2, y, BUTTON_SIZE, BUTTON_SIZE),
            world,
            if (assemblyToggle) {
                Rect(startX + (BUTTON_SIZE + BUTTON_GAP) * nextIndex, y, BUTTON_SIZE, BUTTON_SIZE)
            } else {
                Rect.EMPTY
            },
        )
    }

    private fun Rect.toScreen(context: BookElementRenderContext): Rect {
        if (width <= 0 || height <= 0) return Rect.EMPTY
        val relativeX = x - context.x
        val relativeY = y - context.y
        return Rect(
            context.screenX + (relativeX * context.scale).roundToInt(),
            context.screenY + (relativeY * context.scale).roundToInt(),
            max(1, (width * context.scale).roundToInt()),
            max(1, (height * context.scale).roundToInt()),
        )
    }

    private fun shiftDown(): Boolean {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) ||
            InputConstants.isKeyDown(InputConstants.KEY_RSHIFT)
    }

    private fun currentScissor(context: BookElementRenderContext): ScreenRectangle =
        context.scissorArea ?: ScreenRectangle(
            context.screenX,
            context.screenY,
            context.screenWidth.coerceAtLeast(1),
            context.screenHeight.coerceAtLeast(1),
        )

    private data class Controls(
        val left: Rect,
        val mode: Rect,
        val right: Rect,
        val world: Rect,
        val assembly: Rect,
    )

    private data class Rect(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
    ) {
        fun contains(
            mouseX: Int,
            mouseY: Int,
            padding: Int = 0,
        ): Boolean =
            mouseX >= x - padding && mouseX < x + width + padding &&
                mouseY >= y - padding && mouseY < y + height + padding

        fun isInside(scissor: ScreenRectangle): Boolean =
            width > 0 && height > 0 &&
                x >= scissor.left() &&
                y >= scissor.top() &&
                x + width <= scissor.right() &&
                y + height <= scissor.bottom()

        companion object {
            val EMPTY = Rect(0, 0, 0, 0)
        }
    }

    private enum class DragMode { ROTATE, PAN }

    private class PreviewState(
        var scale: Float,
        var rotationX: Float,
        var rotationY: Float,
        var offsetX: Float,
        var offsetY: Float,
        var layer: Int,
        var layered: Boolean,
        var assembled: Boolean = false,
    ) {
        var previewBounds = Rect(0, 0, 0, 0)
        var leftButton = Rect(0, 0, 0, 0)
        var modeButton = Rect(0, 0, 0, 0)
        var rightButton = Rect(0, 0, 0, 0)
        var worldButton = Rect(0, 0, 0, 0)
        var assemblyButton = Rect(0, 0, 0, 0)
        var dragMode = DragMode.ROTATE
        var maxLayer = 0
        var lastSeenFrame = -1L
        var hasAssemblyToggle = false
        var canAssemble = false
        var worldMultiblock: Multiblock? = null

        fun transform(): MultiblockPreviewTransform =
            MultiblockPreviewTransform(
                scale = scale,
                rotationX = rotationX,
                rotationY = rotationY,
                offsetX = offsetX,
                offsetY = offsetY,
                layer = if (!assembled && layered) layer else Int.MAX_VALUE,
                singleLayer = !assembled && layered,
            )

        companion object {
            fun from(
                element: MultiblockBookElement,
                multiblock: Multiblock,
            ): PreviewState {
                val layered = element.layer != Int.MAX_VALUE
                return PreviewState(
                    scale = element.scale.coerceIn(MIN_SCALE, MAX_SCALE),
                    rotationX = element.rotationX,
                    rotationY = element.rotationY.coerceIn(MIN_VERTICAL_ROTATION, MAX_VERTICAL_ROTATION),
                    offsetX = 0f,
                    offsetY = 0f,
                    layer = if (layered) element.layer.coerceIn(0, multiblock.ySize - 1) else 0,
                    layered = layered,
                )
            }

            fun from(
                element: AssembledMultiblockBookElement,
                multiblock: AssembledMultiblockDefinition,
            ): PreviewState {
                val layered = element.layer != Int.MAX_VALUE
                return PreviewState(
                    scale = element.scale.coerceIn(MIN_SCALE, MAX_SCALE),
                    rotationX = element.rotationX,
                    rotationY = element.rotationY.coerceIn(MIN_VERTICAL_ROTATION, MAX_VERTICAL_ROTATION),
                    offsetX = 0f,
                    offsetY = 0f,
                    layer = if (layered) element.layer.coerceIn(0, multiblock.ySize - 1) else 0,
                    layered = layered,
                    assembled = element.assembled && multiblock.formedModel != null,
                )
            }
        }
    }

    private const val BUTTON_SIZE = 8
    private const val BUTTON_GAP = 2
    private const val BUTTON_HIT_PADDING = 2
    private const val CONTROL_BOTTOM_MARGIN = 1
    private const val CONTROL_RESERVED_HEIGHT = 14
    private const val ARROW_TEXTURE_WIDTH = 27
    private const val ARROW_TEXTURE_HEIGHT = 23
    private const val FULL_MODE_TEXTURE_SIZE = 16
    private const val LAYERED_MODE_TEXTURE_SIZE = 8
    private const val HAMMER_TEXTURE_SIZE = 16
    private const val DISABLED_ARROW_TINT = 0x66FFFFFF
    private const val ASSEMBLED_BUTTON_COLOR = 0x663A7442
    private const val HOVERED_BUTTON_COLOR = 0x33404040
    private const val LEFT_MOUSE_BUTTON = InputConstants.MOUSE_BUTTON_LEFT
    private const val RIGHT_MOUSE_BUTTON = InputConstants.MOUSE_BUTTON_RIGHT
    private const val ROTATION_SPEED = 0.75f
    private const val ZOOM_STEP = 0.12f
    private const val MIN_SCALE = 0.25f
    private const val MAX_SCALE = 3.0f
    private const val MIN_VERTICAL_ROTATION = -89f
    private const val MAX_VERTICAL_ROTATION = 89f
}
