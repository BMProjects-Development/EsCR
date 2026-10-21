package com.algorithmlx.ecr.client.widget.mim

import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent

object MIMWindowInput {
    fun mouseClicked(screen: Screen, event: MouseButtonEvent, doubleClick: Boolean = false): Boolean {
        val host = screen as? MIMWindowHost ?: return false
        if (!host.isOverlayInputAt(event.x(), event.y())) {
            host.releaseFloatingFocus()
            return false
        }
        screen.mouseClicked(event, doubleClick)
        return true
    }

    fun mouseReleased(screen: Screen, event: MouseButtonEvent): Boolean = route(
        screen, { it.hasOverlayPointerCapture }
    ) { screen.mouseReleased(event) }

    fun mouseDragged(screen: Screen, event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean = route(
        screen, { it.hasOverlayPointerCapture }
    ) { screen.mouseDragged(event, dragX, dragY) }

    fun mouseScrolled(screen: Screen, x: Double, y: Double, horizontal: Double, vertical: Double): Boolean = route(
        screen, { it.isOverlayInputAt(x, y) }
    ) { screen.mouseScrolled(x, y, horizontal, vertical) }

    fun keyPressed(screen: Screen, event: KeyEvent): Boolean = route(
        screen, { it.isFloatingInputFocused || event.isEscape() && it.floatingWindowBounds.isNotEmpty() }
    ) { screen.keyPressed(event) }

    fun keyReleased(screen: Screen, event: KeyEvent): Boolean = route(
        screen, { it.isFloatingInputFocused }
    ) { screen.keyReleased(event) }

    fun charTyped(screen: Screen, event: CharacterEvent): Boolean = route(
        screen, { it.isFloatingTextInput }
    ) { screen.charTyped(event) }

    private inline fun route(screen: Screen, captures: (MIMWindowHost) -> Boolean, handle: () -> Unit): Boolean {
        val host = screen as? MIMWindowHost ?: return false
        if (!captures(host)) return false
        handle()
        return true
    }
}
