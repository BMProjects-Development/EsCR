package com.algorithmlx.ecr.client.book.controller

import com.algorithmlx.ecr.api.research.*
import com.algorithmlx.ecr.api.research.content.BookEntry
import com.algorithmlx.ecr.api.research.content.BookText
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.client.book.BookPageLayout
import com.mojang.blaze3d.platform.cursor.CursorTypes
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.util.Mth
import java.awt.Color
import kotlin.math.pow

class BookBookmarkController {
    private val bookmarkTexture = "textures/gui/book/bookmark.png".ecRL
    private val selectedTexture = "textures/gui/book/bookmark_selected.png".ecRL
    private val confirmButton = Identifier.withDefaultNamespace("container/beacon/confirm")
    private val cancelButton = Identifier.withDefaultNamespace("container/beacon/cancel")
    private val progress = mutableMapOf<BookBookmarkKey, Float>()
    private var pageHover = 0f
    private var target: BookBookmarkKey? = null
    private var targetHadBookmark = false
    private var draftColor = 0xFFFFFFFF.toInt()
    private var hue = 0f
    private var saturation = 0f
    private var value = 1f
    private var pickerX = -1
    private var pickerY = -1
    private var pickerDragOffsetX = 0
    private var pickerDragOffsetY = 0
    private var draggingPicker = false
    private var draggingHue = false
    private var draggingSaturation = false
    private var draggingGlobalSlider = false
    private var globalScroll = 0f
    private var targetGlobalScroll = 0f
    private val globalTooltipCache = mutableMapOf<BookBookmarkKey, List<Component>>()

    val isPickerOpen get() = target != null

    fun restore(
        state: BookViewState,
        screenWidth: Int,
        screenHeight: Int
    ) {
        pickerX = state.pickerX.takeIf { it >= 0 } ?: (screenWidth - PICKER_WIDTH - 8)
        pickerY = state.pickerY.takeIf { it >= 0 } ?: 24
        constrain(screenWidth, screenHeight)
    }

    fun update(
        dt: Float,
        screenWidth: Int,
        screenHeight: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        constrainGlobalScroll(screenHeight)
        globalScroll = approach(globalScroll, targetGlobalScroll, dt)
        ClientResearchState.bookmarks().forEachIndexed { index, bookmark ->
            val key = BookBookmarkKey(bookmark.research, bookmark.spread)
            val current = progress[key] ?: 0f
            val x = globalBookmarkX(screenWidth, screenHeight, current)
            val y = globalBookmarkY(index)
            val hovered = mouseX in x..<x + GLOBAL_BOOKMARK_WIDTH && mouseY in y..<y + GLOBAL_BOOKMARK_HEIGHT
            progress[key] = approach(current, if (hovered) 1f else 0f, dt)
        }
    }

    fun renderGlobal(
        graphics: GuiGraphicsExtractor,
        screenWidth: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val screenHeight = graphics.guiHeight()
        constrainGlobalScroll(screenHeight)

        val scissorLeft = globalScissorLeft(screenWidth, screenHeight)
        val scissorRight = globalScissorRight(screenWidth, screenHeight)
        graphics.enableScissor(scissorLeft, GLOBAL_BOOKMARK_START_Y, scissorRight, screenHeight)
        ClientResearchState.bookmarks().forEachIndexed { index, bookmark ->
            val key = BookBookmarkKey(bookmark.research, bookmark.spread)
            val current = progress[key] ?: 0f
            val x = globalBookmarkX(screenWidth, screenHeight, current)
            val y = globalBookmarkY(index)
            if (y + GLOBAL_BOOKMARK_HEIGHT < GLOBAL_BOOKMARK_START_Y || y > screenHeight) return@forEachIndexed
            val color = 0xFF000000.toInt() or (bookmark.color and 0xFFFFFF)
            renderGlobalBookmark(graphics, x, y, color)
            if (mouseX in x..<x + GLOBAL_BOOKMARK_WIDTH && mouseY in y..<y + GLOBAL_BOOKMARK_HEIGHT) {
                graphics.requestCursor(CursorTypes.POINTING_HAND)
                graphics.setComponentTooltipForNextFrame(
                    Minecraft.getInstance().font,
                    globalBookmarkTooltip(bookmark),
                    mouseX,
                    mouseY
                )
            }
        }
        graphics.disableScissor()

        if (hasGlobalOverflow(screenHeight)) renderGlobalSlider(graphics, screenWidth, screenHeight, mouseX, mouseY)
    }

    fun selectGlobal(
        mouseX: Int,
        mouseY: Int,
        screenWidth: Int,
        screenHeight: Int
    ): BookBookmark? {
        ClientResearchState.bookmarks().forEachIndexed { index, bookmark ->
            val key = BookBookmarkKey(bookmark.research, bookmark.spread)
            val x = globalBookmarkX(screenWidth, screenHeight, progress[key] ?: 0f)
            val y = globalBookmarkY(index)
            if (mouseX in x..<x + GLOBAL_BOOKMARK_WIDTH && mouseY in y..<y + GLOBAL_BOOKMARK_HEIGHT) return bookmark
        }
        return null
    }

    fun clickGlobalSlider(
        mouseX: Int,
        mouseY: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (!hasGlobalOverflow(screenHeight)) return false
        val sliderX = globalSliderX(screenWidth)
        if (mouseX !in sliderX - GLOBAL_SLIDER_CLICK_PADDING..sliderX + GLOBAL_SLIDER_WIDTH + GLOBAL_SLIDER_CLICK_PADDING) return false
        if (mouseY !in GLOBAL_BOOKMARK_START_Y..screenHeight - GLOBAL_BOOKMARK_END_PADDING) return false
        draggingGlobalSlider = true
        updateGlobalSlider(mouseY, screenHeight)
        return true
    }

    fun scrollGlobal(
        mouseX: Int,
        mouseY: Int,
        scrollY: Double,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (!hasGlobalOverflow(screenHeight)) return false
        if (mouseX !in globalInteractionLeft(screenWidth, screenHeight)..globalInteractionRight(screenWidth, screenHeight)) return false
        if (mouseY !in GLOBAL_BOOKMARK_START_Y..screenHeight - GLOBAL_BOOKMARK_END_PADDING) return false
        targetGlobalScroll = (targetGlobalScroll - scrollY.toFloat() * GLOBAL_SCROLL_STEP).coerceIn(0f, maxGlobalScroll(screenHeight))
        return true
    }

    fun renderPage(
        graphics: GuiGraphicsExtractor,
        entry: BookEntry,
        spread: Int,
        hovered: Boolean,
        dt: Float
    ) {
        if (!ClientResearchState.has(entry.id)) return
        pageHover = approach(pageHover, if (hovered) 1f else 0f, dt)
        val bookmark = ClientResearchState.bookmark(entry.id, spread)
        val color = bookmark?.let { 0xFF000000.toInt() or (it.color and 0xFFFFFF) }
        val offset = Mth.lerp(pageHover, -6f, 18f).toInt()
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            if (bookmark == null) bookmarkTexture else selectedTexture,
            BOOKMARK_X + offset,
            BOOKMARK_Y,
            0f,
            0f,
            48,
            16,
            48,
            16,
            color ?: 0xFFFFFFFF.toInt()
        )
    }

    fun activate(
        entry: BookEntry,
        spread: Int,
        editImmediately: Boolean
    ): Boolean {
        if (!ClientResearchState.has(entry.id)) return false
        val bookmark = ClientResearchState.bookmark(entry.id, spread)
        if (bookmark == null && !editImmediately) {
            ResearchNetwork.updateFavorite(entry.id, spread, DEFAULT_COLOR)
            return true
        }
        open(entry, spread)
        return true
    }

    private fun open(
        entry: BookEntry,
        spread: Int
    ) {
        target = BookBookmarkKey(entry.id, spread)
        val color = ClientResearchState.bookmark(entry.id, spread)?.color
        targetHadBookmark = color != null
        draftColor = color ?: DEFAULT_COLOR
        if (color == null) {
            hue = 0f
            saturation = 0f
            value = 1f
        } else {
            val hsb = Color.RGBtoHSB((color shr 16) and 0xFF, (color shr 8) and 0xFF, color and 0xFF, null)
            hue = hsb[0]
            saturation = hsb[1]
            value = hsb[2]
        }
    }

    fun close() {
        target = null
        targetHadBookmark = false
        stopDragging()
    }

    fun click(
        mouseX: Int,
        mouseY: Int
    ): Boolean {
        if (target == null) return false
        val svX = pickerX + PADDING
        val svY = pickerY + HEADER_HEIGHT + PADDING
        val hueX = svX + SV_SIZE + PADDING
        val buttonsY = svY + HUE_HEIGHT + PADDING
        val cancelX = cancelButtonX(hueX)
        val saveX = confirmButtonX(cancelX)
        when (mouseX) {
            in pickerX until pickerX + PICKER_WIDTH if mouseY in pickerY until pickerY + HEADER_HEIGHT -> {
                pickerDragOffsetX = mouseX - pickerX
                pickerDragOffsetY = mouseY - pickerY
                draggingPicker = true
            }

            in svX until svX + SV_SIZE if mouseY in svY until svY + SV_SIZE -> {
                draggingSaturation = true
                updateSaturation(mouseX - svX, mouseY - svY)
            }

            in hueX until hueX + HUE_WIDTH if mouseY in svY until svY + HUE_HEIGHT -> {
                draggingHue = true
                updateHue(mouseY - svY)
            }

            in saveX until saveX + PICKER_BUTTON_SIZE if mouseY in buttonsY until buttonsY + PICKER_BUTTON_SIZE -> {
                target?.let { ResearchNetwork.updateFavorite(it.research, it.spread, draftColor) }
                close()
            }

            in cancelX until cancelX + PICKER_BUTTON_SIZE if mouseY in buttonsY until buttonsY + PICKER_BUTTON_SIZE -> {
                if (targetHadBookmark) target?.let { ResearchNetwork.updateFavorite(it.research, it.spread, null) }
                close()
            }

            else -> {
                close()
            }
        }
        return true
    }

    fun drag(
        mouseX: Int,
        mouseY: Int,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (draggingGlobalSlider) {
            updateGlobalSlider(mouseY, screenHeight)
            return true
        }
        if (target == null) return false
        if (draggingPicker) {
            pickerX = mouseX - pickerDragOffsetX
            pickerY = mouseY - pickerDragOffsetY
            constrain(screenWidth, screenHeight)
            return true
        }
        val svX = pickerX + PADDING
        val svY = pickerY + HEADER_HEIGHT + PADDING
        if (draggingSaturation) updateSaturation(mouseX - svX, mouseY - svY)
        if (draggingHue) updateHue(mouseY - svY)
        return draggingSaturation || draggingHue
    }

    fun stopDragging() {
        draggingPicker = false
        draggingHue = false
        draggingSaturation = false
        draggingGlobalSlider = false
    }

    fun renderPicker(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int
    ) {
        if (target == null) return
        val svX = pickerX + PADDING
        val svY = pickerY + HEADER_HEIGHT + PADDING
        graphics.fill(pickerX, pickerY, pickerX + PICKER_WIDTH, pickerY + PICKER_HEIGHT, 0xF0141820.toInt())
        graphics.fill(pickerX, pickerY, pickerX + PICKER_WIDTH, pickerY + HEADER_HEIGHT, 0xFF3A4656.toInt())
        val hueColor = 0xFF000000.toInt() or (Color.HSBtoRGB(hue, 1f, 1f) and 0xFFFFFF)
        graphics.fill(svX, svY, svX + SV_SIZE, svY + SV_SIZE, hueColor)
        repeat(SV_SIZE) { x ->
            val alpha = ((1f - x.toFloat() / SV_SIZE) * 255).toInt()
            graphics.fill(svX + x, svY, svX + x + 1, svY + SV_SIZE, (alpha shl 24) or 0xFFFFFF)
        }
        repeat(SV_SIZE) { y ->
            val alpha = (y.toFloat() / SV_SIZE * 255).toInt()
            graphics.fill(svX, svY + y, svX + SV_SIZE, svY + y + 1, alpha shl 24)
        }
        val cursorX = svX + (saturation * SV_SIZE).toInt()
        val cursorY = svY + ((1f - value) * SV_SIZE).toInt()
        graphics.fill(cursorX - 2, cursorY - 2, cursorX + 3, cursorY + 3, 0xFFFFFFFF.toInt())
        graphics.fill(cursorX - 1, cursorY - 1, cursorX + 2, cursorY + 2, 0xFF000000.toInt())
        val hueX = svX + SV_SIZE + PADDING
        repeat(HUE_HEIGHT) { y ->
            val color = 0xFF000000.toInt() or (Color.HSBtoRGB(y.toFloat() / HUE_HEIGHT, 1f, 1f) and 0xFFFFFF)
            graphics.fill(hueX, svY + y, hueX + HUE_WIDTH, svY + y + 1, color)
        }
        val hueY = svY + (hue * HUE_HEIGHT).toInt()
        graphics.fill(hueX - 1, hueY, hueX + HUE_WIDTH + 1, hueY + 1, 0xFFFFFFFF.toInt())
        val buttonsY = svY + HUE_HEIGHT + PADDING
        val cancelX = cancelButtonX(hueX)
        val confirmX = confirmButtonX(cancelX)
        if (isInsidePickerClickable(mouseX, mouseY, svX, svY, hueX, confirmX, cancelX, buttonsY)) {
            graphics.requestCursor(CursorTypes.POINTING_HAND)
        }
        renderButton(graphics, confirmX, buttonsY, true)
        renderButton(graphics, cancelX, buttonsY, false)
    }

    fun appendTo(state: BookViewState): BookViewState = state.copy(pickerX = pickerX, pickerY = pickerY)

    fun graphSafeLeft(screenHeight: Int): Float =
        if (hasGlobalBookmarks() &&
            globalBookmarksOnLeft
        ) {
            globalSafeWidth(screenHeight).toFloat()
        } else {
            0f
        }

    fun graphSafeRight(screenHeight: Int): Float =
        if (hasGlobalBookmarks() &&
            !globalBookmarksOnLeft
        ) {
            globalSafeWidth(screenHeight).toFloat()
        } else {
            0f
        }

    private fun renderGlobalSlider(
        graphics: GuiGraphicsExtractor,
        screenWidth: Int,
        screenHeight: Int,
        mouseX: Int,
        mouseY: Int
    ) {
        val trackHeight = globalBookmarkViewportHeight(screenHeight)
        val contentHeight = globalBookmarksHeight()
        val thumbHeight =
            (trackHeight * (trackHeight.toFloat() / contentHeight))
                .toInt()
                .coerceAtLeast(
                    GLOBAL_SLIDER_MIN_THUMB
                ).coerceAtMost(trackHeight)
        val maxScroll = maxGlobalScroll(screenHeight)
        val thumbY =
            if (maxScroll <=
                0f
            ) {
                GLOBAL_BOOKMARK_START_Y
            } else {
                GLOBAL_BOOKMARK_START_Y + ((trackHeight - thumbHeight) * (globalScroll / maxScroll)).toInt()
            }
        val sliderX = globalSliderX(screenWidth)
        if (mouseX in sliderX - GLOBAL_SLIDER_CLICK_PADDING..sliderX + GLOBAL_SLIDER_WIDTH + GLOBAL_SLIDER_CLICK_PADDING &&
            mouseY in GLOBAL_BOOKMARK_START_Y..screenHeight - GLOBAL_BOOKMARK_END_PADDING
        ) {
            graphics.requestCursor(CursorTypes.POINTING_HAND)
        }
        graphics.fill(
            sliderX,
            GLOBAL_BOOKMARK_START_Y,
            sliderX + GLOBAL_SLIDER_WIDTH,
            GLOBAL_BOOKMARK_START_Y + trackHeight,
            0x80101820.toInt()
        )
        graphics.fill(sliderX, thumbY, sliderX + GLOBAL_SLIDER_WIDTH, thumbY + thumbHeight, 0xFFD0D8E8.toInt())
    }

    private fun updateGlobalSlider(
        mouseY: Int,
        screenHeight: Int
    ) {
        if (!hasGlobalOverflow(screenHeight)) return
        val trackHeight = globalBookmarkViewportHeight(screenHeight)
        val contentHeight = globalBookmarksHeight()
        val thumbHeight =
            (trackHeight * (trackHeight.toFloat() / contentHeight))
                .toInt()
                .coerceAtLeast(
                    GLOBAL_SLIDER_MIN_THUMB
                ).coerceAtMost(trackHeight)
        val ratio = ((mouseY - GLOBAL_BOOKMARK_START_Y - thumbHeight / 2f) / (trackHeight - thumbHeight).coerceAtLeast(1)).coerceIn(0f, 1f)
        targetGlobalScroll = ratio * maxGlobalScroll(screenHeight)
    }

    private fun globalBookmarkX(
        screenWidth: Int,
        screenHeight: Int,
        progress: Float
    ): Int {
        val fullX = globalBookmarkFullX(screenWidth, screenHeight)
        return if (globalBookmarksOnLeft) {
            Mth.lerp(progress, (fullX - GLOBAL_BOOKMARK_HIDDEN_WIDTH).toFloat(), fullX.toFloat()).toInt()
        } else {
            Mth.lerp(progress, (fullX + GLOBAL_BOOKMARK_HIDDEN_WIDTH).toFloat(), fullX.toFloat()).toInt()
        }
    }

    private fun globalBookmarkFullX(
        screenWidth: Int,
        screenHeight: Int
    ): Int {
        if (!hasGlobalOverflow(screenHeight)) return if (globalBookmarksOnLeft) 0 else screenWidth - GLOBAL_BOOKMARK_WIDTH
        return if (globalBookmarksOnLeft) {
            GLOBAL_SLIDER_WIDTH + GLOBAL_BOOKMARK_SLIDER_GAP
        } else {
            screenWidth - GLOBAL_SLIDER_WIDTH - GLOBAL_BOOKMARK_SLIDER_GAP - GLOBAL_BOOKMARK_WIDTH
        }
    }

    private fun globalBookmarkY(index: Int): Int = GLOBAL_BOOKMARK_START_Y + index * GLOBAL_BOOKMARK_STEP - globalScroll.toInt()

    private fun globalSliderX(screenWidth: Int): Int = if (globalBookmarksOnLeft) 0 else screenWidth - GLOBAL_SLIDER_WIDTH

    private fun globalScissorLeft(
        screenWidth: Int,
        screenHeight: Int
    ): Int = if (globalBookmarksOnLeft) globalBookmarkFullX(screenWidth, screenHeight).coerceAtLeast(0) else 0

    private fun globalScissorRight(
        screenWidth: Int,
        screenHeight: Int
    ): Int = if (globalBookmarksOnLeft) screenWidth else globalBookmarkFullX(screenWidth, screenHeight) + GLOBAL_BOOKMARK_WIDTH

    private fun globalInteractionLeft(
        screenWidth: Int,
        screenHeight: Int
    ): Int = if (globalBookmarksOnLeft) 0 else globalBookmarkFullX(screenWidth, screenHeight)

    private fun globalInteractionRight(
        screenWidth: Int,
        screenHeight: Int
    ): Int = if (globalBookmarksOnLeft) globalBookmarkFullX(screenWidth, screenHeight) + GLOBAL_BOOKMARK_WIDTH else screenWidth

    private fun renderGlobalBookmark(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        color: Int
    ) {
        if (globalBookmarksOnLeft) {
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                selectedTexture,
                x,
                y,
                0f,
                0f,
                GLOBAL_BOOKMARK_WIDTH,
                GLOBAL_BOOKMARK_HEIGHT,
                GLOBAL_BOOKMARK_WIDTH,
                GLOBAL_BOOKMARK_HEIGHT,
                color
            )
            return
        }

        repeat(GLOBAL_BOOKMARK_WIDTH) { column ->
            graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                selectedTexture,
                x + column,
                y,
                (GLOBAL_BOOKMARK_WIDTH - column - 1).toFloat(),
                0f,
                1,
                GLOBAL_BOOKMARK_HEIGHT,
                GLOBAL_BOOKMARK_WIDTH,
                GLOBAL_BOOKMARK_HEIGHT,
                color
            )
        }
    }

    private fun isInsidePickerClickable(
        mouseX: Int,
        mouseY: Int,
        svX: Int,
        svY: Int,
        hueX: Int,
        confirmX: Int,
        cancelX: Int,
        buttonsY: Int
    ): Boolean =
        mouseX in pickerX until pickerX + PICKER_WIDTH && mouseY in pickerY until pickerY + HEADER_HEIGHT ||
            mouseX in svX until svX + SV_SIZE && mouseY in svY until svY + SV_SIZE ||
            mouseX in hueX until hueX + HUE_WIDTH && mouseY in svY until svY + HUE_HEIGHT ||
            mouseX in confirmX until confirmX + PICKER_BUTTON_SIZE && mouseY in buttonsY until buttonsY + PICKER_BUTTON_SIZE ||
            mouseX in cancelX until cancelX + PICKER_BUTTON_SIZE && mouseY in buttonsY until buttonsY + PICKER_BUTTON_SIZE

    private fun globalSafeWidth(screenHeight: Int): Int =
        GLOBAL_BOOKMARK_WIDTH + if (hasGlobalOverflow(screenHeight)) GLOBAL_SLIDER_WIDTH + GLOBAL_BOOKMARK_SLIDER_GAP else 0

    private fun hasGlobalBookmarks(): Boolean = ClientResearchState.bookmarks().isNotEmpty()

    private fun cancelButtonX(hueX: Int): Int = hueX + (HUE_WIDTH - PICKER_BUTTON_SIZE) / 2

    private fun confirmButtonX(cancelX: Int): Int = cancelX - PICKER_BUTTON_SIZE - PICKER_BUTTON_GAP

    private fun hasGlobalOverflow(screenHeight: Int): Boolean = maxGlobalScroll(screenHeight) > 0f

    private fun maxGlobalScroll(screenHeight: Int): Float =
        (globalBookmarksHeight() - globalBookmarkViewportHeight(screenHeight)).coerceAtLeast(0).toFloat()

    private fun globalBookmarksHeight(): Int = ClientResearchState.bookmarks().size * GLOBAL_BOOKMARK_STEP

    private fun globalBookmarkViewportHeight(screenHeight: Int): Int =
        (screenHeight - GLOBAL_BOOKMARK_START_Y - GLOBAL_BOOKMARK_END_PADDING).coerceAtLeast(1)

    private fun constrainGlobalScroll(screenHeight: Int) {
        val maxScroll = maxGlobalScroll(screenHeight)
        targetGlobalScroll = targetGlobalScroll.coerceIn(0f, maxScroll)
        globalScroll = globalScroll.coerceIn(0f, maxScroll)
    }

    private fun updateSaturation(
        x: Int,
        y: Int
    ) {
        saturation = (x.toFloat() / SV_SIZE).coerceIn(0f, 1f)
        value = 1f - (y.toFloat() / SV_SIZE).coerceIn(0f, 1f)
        applyColor()
    }

    private fun updateHue(y: Int) {
        hue = (y.toFloat() / HUE_HEIGHT).coerceIn(0f, 1f)
        applyColor()
    }

    private fun applyColor() {
        draftColor = 0xFF000000.toInt() or (Color.HSBtoRGB(hue, saturation, value) and 0xFFFFFF)
    }

    private fun renderButton(
        graphics: GuiGraphicsExtractor,
        x: Int,
        y: Int,
        confirm: Boolean
    ) {
        graphics.blitSprite(
            RenderPipelines.GUI_TEXTURED,
            if (confirm) confirmButton else cancelButton,
            x,
            y,
            PICKER_BUTTON_SIZE,
            PICKER_BUTTON_SIZE
        )
    }

    private fun constrain(
        screenWidth: Int,
        screenHeight: Int
    ) {
        pickerX = pickerX.coerceIn(0, (screenWidth - PICKER_WIDTH).coerceAtLeast(0))
        pickerY = pickerY.coerceIn(0, (screenHeight - PICKER_HEIGHT).coerceAtLeast(0))
    }

    private fun approach(
        current: Float,
        target: Float,
        dt: Float
    ): Float {
        if (dt <= 0f) return target
        return target + (current - target) * 0.5f.pow(dt / 0.08f)
    }

    private fun globalBookmarkTooltip(bookmark: BookBookmark): List<Component> {
        val key = BookBookmarkKey(bookmark.research, bookmark.spread)
        return globalTooltipCache.getOrPut(key) {
            val entry = ResearchCatalog.snapshot().entries[bookmark.research]
            val title =
                entry?.title?.component()
                    ?: Component.literal(bookmark.research.toString())
            val spreadCount = entry?.let { BookPageLayout.paginate(it).size } ?: (bookmark.spread + 1)
            listOf(title, pageTooltip(bookmark.spread, spreadCount))
        }
    }

    private fun pageTooltip(
        spread: Int,
        spreadCount: Int
    ): Component =
        Component
            .literal(
                "Page ${spread + 1}/${spreadCount.coerceAtLeast(1)}"
            ).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)

    private fun BookText.component(): Component = if (translated) Component.translatable(value) else Component.literal(value)

    private data class BookBookmarkKey(
        val research: Identifier,
        val spread: Int
    )

    companion object {
        const val BOOKMARK_X = 484
        const val BOOKMARK_Y = 16

        @JvmField
        var globalBookmarksOnLeft = false
        private const val CATEGORY_HEIGHT = 16
        private const val GLOBAL_BOOKMARK_WIDTH = 48
        private const val GLOBAL_BOOKMARK_HEIGHT = 16
        private const val GLOBAL_BOOKMARK_VISIBLE_WIDTH = 16
        private const val GLOBAL_BOOKMARK_HIDDEN_WIDTH = GLOBAL_BOOKMARK_WIDTH - GLOBAL_BOOKMARK_VISIBLE_WIDTH
        private const val GLOBAL_BOOKMARK_STEP = 20
        private const val GLOBAL_BOOKMARK_START_Y = CATEGORY_HEIGHT + 4
        private const val GLOBAL_BOOKMARK_END_PADDING = 4
        private const val GLOBAL_BOOKMARK_SLIDER_GAP = 2
        private const val GLOBAL_SCROLL_STEP = 40f
        private const val GLOBAL_SLIDER_WIDTH = 2
        private const val GLOBAL_SLIDER_MIN_THUMB = 24
        private const val GLOBAL_SLIDER_CLICK_PADDING = 3
        private const val PADDING = 4
        private const val HEADER_HEIGHT = 7
        private const val SV_SIZE = 46
        private const val HUE_WIDTH = 10
        private const val HUE_HEIGHT = 46
        private const val PICKER_BUTTON_SIZE = 10
        private const val PICKER_BUTTON_GAP = 2
        private const val COLOR_ROW_WIDTH = SV_SIZE + PADDING + HUE_WIDTH
        private const val PICKER_WIDTH = PADDING * 2 + COLOR_ROW_WIDTH
        private const val PICKER_HEIGHT = HEADER_HEIGHT + PADDING * 2 + HUE_HEIGHT + PADDING + PICKER_BUTTON_SIZE
        private const val DEFAULT_COLOR = 0xFFFFFFFF.toInt()
    }
}
