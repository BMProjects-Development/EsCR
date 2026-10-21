package com.algorithmlx.ecr.client.widget.mim

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

internal class MIMDependencyScrollBar(
    private val onScroll: (Double) -> Unit,
    label: Component = Component.translatable("screen.escr.mim.dependency.scroll")
) : AbstractWidget(
    0, 0, MIMDependencyTextures.TRACK.width, MIMDependencyLayout.BODY_HEIGHT, label
) {
    private var value = 0.0
    private var maximum = 0.0
    private var page = 1.0
    private var step = 1.0
    private var scale = 1f
    private var draggingThumb = false
    private var pressedArrow = 0
    private var dragOffset = 0.0
    private val arrowHeight: Int get() = (MIMDependencyTextures.UP.height * scale).roundToInt().coerceAtLeast(1)
    private val thumbHeight: Int get() = (MIMDependencyTextures.THUMB.height * scale).roundToInt().coerceAtLeast(1)
    private val travel: Int get() = (height - arrowHeight * 2 - thumbHeight).coerceAtLeast(0)
    private val thumbY: Int get() = y + arrowHeight + if (maximum > 0) (travel * value / maximum).roundToInt() else 0

    fun update(value: Double, maximum: Double, page: Double, step: Double, scale: Float) {
        this.maximum = maximum.coerceAtLeast(0.0)
        this.value = value.coerceIn(0.0, this.maximum)
        this.page = page
        this.step = step
        this.scale = scale
        active = this.maximum > 0
        if (!active || !visible) releasePointer()
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        MIMDependencyTextures.TRACK.draw(graphics, x, y, width, height)
        val hovered = active && mouseX >= x && mouseX < x + width
        val up = when {
            pressedArrow < 0 -> MIMDependencyTextures.UP_PRESSED
            hovered && mouseY >= y && mouseY < y + arrowHeight -> MIMDependencyTextures.UP_HOVERED
            else -> MIMDependencyTextures.UP
        }
        val down = when {
            pressedArrow > 0 -> MIMDependencyTextures.DOWN_PRESSED
            hovered && mouseY >= y + height - arrowHeight && mouseY < y + height -> MIMDependencyTextures.DOWN_HOVERED
            else -> MIMDependencyTextures.DOWN
        }
        val thumb = when {
            draggingThumb -> MIMDependencyTextures.THUMB_PRESSED
            hovered && mouseY >= thumbY && mouseY < thumbY + thumbHeight -> MIMDependencyTextures.THUMB_HOVERED
            else -> MIMDependencyTextures.THUMB
        }
        up.draw(graphics, x, y, width, arrowHeight)
        down.draw(graphics, x, y + height - arrowHeight, width, arrowHeight)
        thumb.draw(graphics, x, thumbY, width, thumbHeight)
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        when {
            event.y() < y + arrowHeight -> {
                pressedArrow = -1
                select(value - step)
            }
            event.y() >= y + height - arrowHeight -> {
                pressedArrow = 1
                select(value + step)
            }
            else -> {
                draggingThumb = true
                dragOffset = if (event.y() >= thumbY && event.y() < thumbY + thumbHeight) event.y() - thumbY else thumbHeight / 2.0
                scrollTo(event.y())
            }
        }
    }

    override fun onDrag(event: MouseButtonEvent, dragX: Double, dragY: Double) {
        if (draggingThumb) scrollTo(event.y())
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) releasePointer()
        return super.mouseReleased(event)
    }

    fun releasePointer() {
        draggingThumb = false
        pressedArrow = 0
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (!active || vertical == 0.0) return false
        select(value - vertical * step)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!isFocused || !active) return false
        select(when (event.key()) {
            InputConstants.KEY_UP -> value - step
            InputConstants.KEY_DOWN -> value + step
            InputConstants.KEY_PAGEUP -> value - page
            InputConstants.KEY_PAGEDOWN -> value + page
            InputConstants.KEY_HOME -> 0.0
            InputConstants.KEY_END -> maximum
            else -> return false
        })
        return true
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, message)
        output.add(NarratedElementType.POSITION, Component.literal("${(value / maximum.coerceAtLeast(1.0) * 100).roundToInt()}%"))
    }

    private fun scrollTo(mouseY: Double) {
        if (travel > 0) select((mouseY - y - arrowHeight - dragOffset) / travel * maximum)
    }

    private fun select(next: Double) {
        val clamped = next.coerceIn(0.0, maximum)
        if (value == clamped) return
        value = clamped
        onScroll(value)
    }
}
