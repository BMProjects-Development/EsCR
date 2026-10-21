package com.algorithmlx.ecr.client.widget.mim

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.navigation.ScreenRectangle

interface MIMWindowHost {
    val floatingWindowBounds: List<ScreenRectangle>
    val overlayExclusionBounds: List<ScreenRectangle> get() = floatingWindowBounds
    val hasOverlayPointerCapture: Boolean
    val isFloatingTextInput: Boolean
    val isFloatingInputFocused: Boolean
    fun releaseFloatingFocus()
    fun extractFloatingWindows(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float)

    fun isFloatingWindowAt(x: Double, y: Double): Boolean = floatingWindowBounds.any {
        x >= it.left() && x < it.right() && y >= it.top() && y < it.bottom()
    }

    fun isOverlayInputAt(x: Double, y: Double): Boolean = overlayExclusionBounds.any {
        x >= it.left() && x < it.right() && y >= it.top() && y < it.bottom()
    }
}
