package com.algorithmlx.ecr.client.widget.mim

import com.mojang.blaze3d.platform.InputConstants
import com.algorithmlx.ecr.client.utils.MIMGuiTextures
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import kotlin.math.roundToInt

internal class MIMScrollBar(
    initialX: Int, initialY: Int, initialHeight: Int, private val onScroll: (Int) -> Unit,
    private val skin: MIMGuiTextures.ScrollThumb = MIMGuiTextures.TERMINAL_SCROLLBAR
) : AbstractWidget(initialX, initialY, WIDTH, initialHeight, Component.translatable("screen.escr.mim.scroll")) {
    private var row = 0
    private var maxRow = 0
    private var visibleRows = 1
    private var dragOffset = 0.0
    private var wheelRemainder = 0.0
    private val thumbHeight: Int get() = THUMB_HEIGHT.coerceAtMost(height)
    private val thumbY: Int get() = y + if (maxRow == 0) 0 else ((height - thumbHeight) * row.toDouble() / maxRow).roundToInt()

    fun update(row: Int, maxRow: Int, visibleRows: Int) {
        this.maxRow = maxRow.coerceAtLeast(0)
        this.row = row.coerceIn(0, this.maxRow)
        this.visibleRows = visibleRows.coerceAtLeast(1)
        active = this.maxRow > 0
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        skin.draw(graphics, x, thumbY, thumbHeight, active)
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) {
        dragOffset = if (event.y() >= thumbY && event.y() < thumbY + thumbHeight) event.y() - thumbY else thumbHeight / 2.0
        scrollTo(event.y())
    }

    override fun onDrag(event: MouseButtonEvent, dragX: Double, dragY: Double) = scrollTo(event.y())

    override fun mouseScrolled(mouseX: Double, mouseY: Double, horizontal: Double, vertical: Double): Boolean {
        if (!active || vertical == 0.0) return false
        wheelRemainder -= vertical
        val steps = wheelRemainder.toInt()
        wheelRemainder -= steps
        if (steps != 0) select(row + steps)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (!isFocused || !active) return false
        val target = when (event.key()) {
            InputConstants.KEY_UP -> row - 1
            InputConstants.KEY_DOWN -> row + 1
            InputConstants.KEY_PAGEUP -> row - visibleRows
            InputConstants.KEY_PAGEDOWN -> row + visibleRows
            InputConstants.KEY_HOME -> 0
            InputConstants.KEY_END -> maxRow
            else -> return false
        }
        select(target)
        return true
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, message)
        output.add(NarratedElementType.POSITION, Component.translatable("screen.escr.mim.scroll.position", row + 1, maxRow + 1))
    }

    private fun scrollTo(mouseY: Double) {
        val travel = height - thumbHeight
        if (travel > 0) select(((mouseY - y - dragOffset) / travel * maxRow).roundToInt())
    }

    private fun select(value: Int) {
        val next = value.coerceIn(0, maxRow)
        if (next == row) return
        row = next
        onScroll(next)
    }

    companion object {
        const val WIDTH = 12
        private const val THUMB_HEIGHT = 15
    }
}
