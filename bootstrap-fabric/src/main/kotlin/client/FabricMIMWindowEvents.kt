package com.algorithmlx.ecr.fabric.client

import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.client.widget.mim.MIMWindowHost
import com.algorithmlx.ecr.client.widget.mim.MIMWindowInput
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.event.Event

object FabricMIMWindowEvents {
    private val INPUT_PHASE = "mim_windows".ecRL

    fun init() {
        ScreenEvents.AFTER_INIT.register { _, screen, _, _ ->
            if (screen !is MIMWindowHost) return@register
            early(ScreenMouseEvents.allowMouseClick(screen), ScreenMouseEvents.AllowMouseClick { target, event ->
                !MIMWindowInput.mouseClicked(target, event)
            })
            early(ScreenMouseEvents.allowMouseRelease(screen), ScreenMouseEvents.AllowMouseRelease { target, event ->
                !MIMWindowInput.mouseReleased(target, event)
            })
            early(ScreenMouseEvents.allowMouseDrag(screen), ScreenMouseEvents.AllowMouseDrag { target, event, x, y ->
                !MIMWindowInput.mouseDragged(target, event, x, y)
            })
            early(ScreenMouseEvents.allowMouseScroll(screen), ScreenMouseEvents.AllowMouseScroll { target, x, y, horizontal, vertical ->
                !MIMWindowInput.mouseScrolled(target, x, y, horizontal, vertical)
            })
            early(ScreenKeyboardEvents.allowKeyPress(screen), ScreenKeyboardEvents.AllowKeyPress { target, event ->
                !MIMWindowInput.keyPressed(target, event)
            })
            early(ScreenKeyboardEvents.allowKeyRelease(screen), ScreenKeyboardEvents.AllowKeyRelease { target, event ->
                !MIMWindowInput.keyReleased(target, event)
            })
            early(ScreenKeyboardEvents.allowCharType(screen), ScreenKeyboardEvents.AllowCharType { target, event ->
                !MIMWindowInput.charTyped(target, event)
            })
        }
    }

    private fun <T : Any> early(event: Event<T>, listener: T) {
        event.addPhaseOrdering(INPUT_PHASE, Event.DEFAULT_PHASE)
        event.register(INPUT_PHASE, listener)
    }
}
