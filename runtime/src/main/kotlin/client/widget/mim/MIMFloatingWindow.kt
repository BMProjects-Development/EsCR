package com.algorithmlx.ecr.client.widget.mim

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Renderable
import net.minecraft.client.gui.components.events.ContainerEventHandler
import net.minecraft.client.gui.narration.NarratableEntry
import net.minecraft.client.input.MouseButtonEvent
import kotlin.math.roundToInt
import com.algorithmlx.ecr.common.menu.MIMMenu

interface MIMFloatingWindow : ContainerEventHandler, Renderable, NarratableEntry {
    val x: Int
    val y: Int
    val windowWidth: Int
    val windowHeight: Int
    val visible: Boolean
    val isEditing: Boolean get() = false
    fun update(menu: MIMMenu)
    fun close()
    fun fitViewport(width: Int, height: Int, initialX: Int, initialY: Int)
    fun releasePointer()
    fun extractTooltip(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int): Boolean
}

internal class MIMWindowFrame(
    private val width: Int, private val height: Int, private val titleHeight: Int, private val moved: () -> Unit
) {
    var x = 0
        private set
    var y = 0
        private set
    private var viewportWidth = width
    private var viewportHeight = height
    private var positioned = false
    private var dragging = false
    private var offsetX = 0.0
    private var offsetY = 0.0
    private var scale = 1f

    fun fitViewport(width: Int, height: Int, initialX: Int, initialY: Int, scale: Float = 1f) {
        viewportWidth = width
        viewportHeight = height
        this.scale = scale
        move(if (positioned) x else initialX, if (positioned) y else initialY)
        positioned = true
    }

    fun contains(mouseX: Double, mouseY: Double): Boolean = mouseX >= x && mouseX < x + width * scale && mouseY >= y && mouseY < y + height * scale

    fun begin(event: MouseButtonEvent) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && event.y() < y + titleHeight * scale) {
            dragging = true
            offsetX = event.x() - x
            offsetY = event.y() - y
        }
    }

    fun drag(event: MouseButtonEvent): Boolean {
        if (!dragging || event.button() != InputConstants.MOUSE_BUTTON_LEFT) return false
        move((event.x() - offsetX).roundToInt(), (event.y() - offsetY).roundToInt())
        return true
    }

    fun release() { dragging = false }

    private fun move(x: Int, y: Int) {
        this.x = x.coerceIn(0, (viewportWidth - (width * scale).roundToInt()).coerceAtLeast(0))
        this.y = y.coerceIn(0, (viewportHeight - (height * scale).roundToInt()).coerceAtLeast(0))
        moved()
    }
}
