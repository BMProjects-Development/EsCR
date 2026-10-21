package com.algorithmlx.ecr.client.widget.mim

import com.algorithmlx.ecr.client.utils.MIMGuiTextures
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.EditBox
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.client.input.PreeditEvent
import net.minecraft.network.chat.Component

internal class MIMQuantityBox(font: Font, changed: () -> Unit) : AbstractWidget(
    0, 0, MIMDependencyTextures.QUANTITY.width, MIMDependencyTextures.QUANTITY.height,
    Component.translatable("screen.escr.mim.batches")
) {
    private val texture = MIMDependencyTextures.QUANTITY
    private val editor = object : EditBox(font, 2, (texture.height - font.lineHeight) / 2, texture.width - 4, font.lineHeight, message) {
        override fun insertText(text: String) = super.insertText(text.filter { it in '0'..'9' })
    }
    var value: String
        get() = editor.value
        set(value) { editor.value = value }

    init {
        editor.setMaxLength(Int.MAX_VALUE.toString().length)
        editor.setBordered(false)
        editor.setCentered(true)
        editor.setTextShadow(false)
        editor.setTextColor(MIMGuiTextures.TEXT)
        editor.setTextColorUneditable(MIMGuiTextures.TEXT)
        editor.setResponder { changed() }
    }

    override fun extractWidgetRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        texture.draw(graphics, x, y, width, height)
        editor.active = active
        graphics.pose().pushMatrix()
        graphics.pose().translate(x.toFloat(), y.toFloat())
        graphics.pose().scale(width.toFloat() / texture.width, height.toFloat() / texture.height)
        editor.extractRenderState(graphics, localX(mouseX.toDouble()).toInt(), localY(mouseY.toDouble()).toInt(), deltaTicks)
        graphics.pose().popMatrix()
    }

    override fun onClick(event: MouseButtonEvent, doubleClick: Boolean) = editor.onClick(local(event), doubleClick)
    override fun onDrag(event: MouseButtonEvent, dragX: Double, dragY: Double) {
        editor.mouseDragged(local(event), dragX * texture.width / width, dragY * texture.height / height)
    }

    override fun setFocused(focused: Boolean) {
        super.setFocused(focused)
        editor.isFocused = focused
    }

    override fun keyPressed(event: KeyEvent): Boolean = active && editor.keyPressed(event)
    override fun capturesInput(): Boolean = active && editor.capturesInput()
    override fun charTyped(event: CharacterEvent): Boolean = active && editor.charTyped(event)
    override fun preeditUpdated(event: PreeditEvent?): Boolean = active && editor.preeditUpdated(event)
    override fun updateWidgetNarration(output: NarrationElementOutput) = editor.updateNarration(output)

    private fun localX(value: Double): Double = (value - x) * texture.width / width
    private fun localY(value: Double): Double = (value - y) * texture.height / height
    private fun local(event: MouseButtonEvent) = MouseButtonEvent(localX(event.x()), localY(event.y()), event.buttonInfo())
}
