package com.algorithmlx.ecr.neoforge.client

import com.algorithmlx.ecr.client.widget.mim.MIMWindowInput
import net.neoforged.bus.api.EventPriority
import net.neoforged.neoforge.client.event.ScreenEvent
import net.neoforged.neoforge.common.NeoForge

object NeoForgeMIMWindowEvents {
    fun init() {
        val bus = NeoForge.EVENT_BUS
        bus.addListener(EventPriority.HIGHEST, ::clicked)
        bus.addListener(EventPriority.HIGHEST, ::released)
        bus.addListener(EventPriority.HIGHEST, ::dragged)
        bus.addListener(EventPriority.HIGHEST, ::scrolled)
        bus.addListener(EventPriority.HIGHEST, ::keyPressed)
        bus.addListener(EventPriority.HIGHEST, ::keyReleased)
        bus.addListener(EventPriority.HIGHEST, ::typed)
    }

    private fun clicked(event: ScreenEvent.MouseButtonPressed.Pre) {
        if (MIMWindowInput.mouseClicked(event.screen, event.mouseButtonEvent, event.isDoubleClick)) event.isCanceled = true
    }

    private fun released(event: ScreenEvent.MouseButtonReleased.Pre) {
        if (MIMWindowInput.mouseReleased(event.screen, event.mouseButtonEvent)) event.isCanceled = true
    }

    private fun dragged(event: ScreenEvent.MouseDragged.Pre) {
        if (MIMWindowInput.mouseDragged(event.screen, event.mouseButtonEvent, event.dragX, event.dragY)) event.isCanceled = true
    }

    private fun scrolled(event: ScreenEvent.MouseScrolled.Pre) {
        if (MIMWindowInput.mouseScrolled(event.screen, event.mouseX, event.mouseY, event.scrollDeltaX, event.scrollDeltaY)) event.isCanceled = true
    }

    private fun keyPressed(event: ScreenEvent.KeyPressed.Pre) {
        if (MIMWindowInput.keyPressed(event.screen, event.keyEvent)) event.isCanceled = true
    }

    private fun keyReleased(event: ScreenEvent.KeyReleased.Pre) {
        if (MIMWindowInput.keyReleased(event.screen, event.keyEvent)) event.isCanceled = true
    }

    private fun typed(event: ScreenEvent.CharacterTyped.Pre) {
        if (MIMWindowInput.charTyped(event.screen, event.characterEvent)) event.isCanceled = true
    }
}
