package com.algorithmlx.ecr.client.widget.mim

import net.minecraft.client.gui.components.Button
import net.minecraft.client.input.InputWithModifiers
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component

abstract class MIMPressedButton(
    x: Int, y: Int, width: Int, height: Int, label: Component, action: () -> Unit
) : Button(x, y, width, height, label, { action() }, DEFAULT_NARRATION) {
    private var heldMouse: Int? = null
    private var heldKey: Int? = null
    val isPressed: Boolean get() = visible && active && (heldMouse != null || heldKey != null)

    override fun onPress(input: InputWithModifiers) {
        if (input is MouseButtonEvent) heldMouse = input.button()
        if (input is KeyEvent) heldKey = input.key()
        super.onPress(input)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (heldMouse == event.button()) heldMouse = null
        return super.mouseReleased(event)
    }

    override fun keyReleased(event: KeyEvent): Boolean {
        if (heldKey != event.key()) return super.keyReleased(event)
        heldKey = null
        return true
    }

    override fun setFocused(focused: Boolean) {
        if (!focused) releasePointer()
        super.setFocused(focused)
    }

    fun releasePointer() {
        heldMouse = null
        heldKey = null
    }
}
