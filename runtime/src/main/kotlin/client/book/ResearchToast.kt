package com.algorithmlx.ecr.client.book

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.research.content.BookEntry
import com.algorithmlx.ecr.api.research.content.BookIcon
import com.algorithmlx.ecr.api.research.content.BookText
import com.algorithmlx.ecr.api.research.ResearchCatalog
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.toasts.Toast
import net.minecraft.client.gui.components.toasts.ToastManager
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import kotlin.math.ceil
import kotlin.math.max

class ResearchToast private constructor(
    private val entry: BookEntry,
    private val type: Type
) : Toast {
    private var visibility = Toast.Visibility.SHOW

    override fun getWantedVisibility(): Toast.Visibility = visibility

    override fun update(manager: ToastManager, time: Long) {
        val duration = ANIMATION_TIME + (DISPLAY_TIME * manager.notificationDisplayTimeMultiplier).toLong()
        visibility = if (time < duration) Toast.Visibility.SHOW else Toast.Visibility.HIDE
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, font: Font, time: Long) {
        val contentHeight = contentHeight(font)
        graphics.fill(0, 0, width(), contentHeight, 0xF00A101A.toInt())
        graphics.outline(0, 0, width(), contentHeight, 0xFF8EB9E8.toInt())
        graphics.fill(1, 1, width() - 1, 2, 0xFFDBF1FF.toInt())
        renderIcon(graphics, ICON_X, (contentHeight - ICON_SIZE) / 2)
        renderResearch(graphics, font, time, contentHeight)
    }

    override fun width(): Int = WIDTH

    override fun height(): Int = contentHeight(Minecraft.getInstance().font) + GAP

    override fun yPos(slot: Int): Float = slot * (SLOT_HEIGHT + GAP).toFloat()

    override fun occcupiedSlotCount(): Int = ceil(contentHeight(Minecraft.getInstance().font) / SLOT_HEIGHT.toFloat()).toInt()

    private fun renderResearch(graphics: GuiGraphicsExtractor, font: Font, time: Long, contentHeight: Int) {
        val introProgress = (time / INTRO_FADE_OUT.toFloat()).coerceIn(0f, 1f)
        val detailsProgress = ((time - INTRO_FADE_OUT - PAUSE_TIME) / DETAILS_FADE_IN.toFloat()).coerceIn(0f, 1f)
        val introAlpha = ((1f - introProgress) * 255).toInt().coerceIn(0, 255)
        if (introAlpha > 0) {
            graphics.text(
                font,
                Component.translatable("toast.$ModId.research_book.${type.translationKey}"),
                TEXT_X,
                (contentHeight - font.lineHeight) / 2,
                (introAlpha shl 24) or 0x9FD5FF,
                false
            )
        }
        if (detailsProgress <= 0f) return

        val alpha = (detailsProgress * 255).toInt().coerceIn(0, 255)
        val titleLines = font.split(entry.title.component(), TEXT_WIDTH)
        val descriptionLines = description()?.let { font.split(it, TEXT_WIDTH).take(2) }.orEmpty()
        val textHeight = textBlockHeight(titleLines.size, descriptionLines.size, font)
        var y = (contentHeight - textHeight) / 2
        titleLines.forEach { line ->
            graphics.text(font, line, TEXT_X, y, (alpha shl 24) or 0xFFFFFF, false)
            y += font.lineHeight
        }
        if (descriptionLines.isNotEmpty()) {
            y += DESCRIPTION_GAP
            descriptionLines.forEach { line ->
                graphics.text(font, line, TEXT_X, y, (alpha shl 24) or 0xC9D1D9, false)
                y += font.lineHeight
            }
        }
    }

    private fun contentHeight(font: Font): Int {
        val titleLines = font.split(entry.title.component(), TEXT_WIDTH).size.coerceAtLeast(1)
        val descriptionLines = description()?.let { font.split(it, TEXT_WIDTH).take(2).size } ?: 0
        val bodyHeight = max(ICON_SIZE, textBlockHeight(titleLines, descriptionLines, font)) + VERTICAL_PADDING * 2
        return max(MIN_CONTENT_HEIGHT, bodyHeight)
    }

    private fun textBlockHeight(titleLines: Int, descriptionLines: Int, font: Font): Int =
        titleLines * font.lineHeight + if (descriptionLines > 0) DESCRIPTION_GAP + descriptionLines * font.lineHeight else 0

    private fun description(): Component? = entry.description
        ?.takeUnless { it.value.isBlank() }
        ?.component()

    private fun renderIcon(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        renderIcon(graphics, entry.icon, x, y)
    }

    private fun renderIcon(graphics: GuiGraphicsExtractor, icon: BookIcon, x: Int, y: Int) {
        icon.texture?.let { texture ->
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, 16, 16, 16, 16)
            return
        }
        icon.item?.let { id ->
            BuiltInRegistries.ITEM.getOptional(id).ifPresent { item -> graphics.item(ItemStack(item), x, y) }
        }
    }

    private fun BookText.component(): Component =
        if (translated) Component.translatable(value) else Component.literal(value)

    companion object {
        fun show(research: Identifier) {
            show(research, Type.COMPLETED)
        }

        fun showUpdated(research: Identifier) {
            show(research, Type.UPDATED)
        }

        private fun show(research: Identifier, type: Type) {
            val entry = ResearchCatalog.snapshot().entries[research] ?: return
            Minecraft.getInstance().gui.toastManager().addToast(ResearchToast(entry, type))
        }

        private const val WIDTH = 160
        private const val MIN_CONTENT_HEIGHT = 32
        private const val SLOT_HEIGHT = 32
        private const val GAP = 4
        private const val ICON_X = 7
        private const val ICON_SIZE = 16
        private const val VERTICAL_PADDING = 7
        private const val DESCRIPTION_GAP = 3
        private const val TEXT_X = 29
        private const val TEXT_WIDTH = WIDTH - TEXT_X - 6
        private const val DISPLAY_TIME = 5000
        private const val INTRO_FADE_OUT = 700L
        private const val PAUSE_TIME = 100L
        private const val DETAILS_FADE_IN = 350L
        private const val ANIMATION_TIME = INTRO_FADE_OUT + PAUSE_TIME + DETAILS_FADE_IN
    }

    private enum class Type(val translationKey: String) {
        COMPLETED("research_completed"),
        UPDATED("research_updated")
    }
}
