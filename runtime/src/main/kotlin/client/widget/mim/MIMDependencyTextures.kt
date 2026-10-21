package com.algorithmlx.ecr.client.widget.mim

import com.algorithmlx.ecr.api.mim.MIMDependencyStatus
import com.algorithmlx.ecr.client.utils.MIMGuiTextures
import com.algorithmlx.ecr.init.ECRModIDs
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component

internal object MIMDependencyTextures {
    private const val TEXTURE_SIZE = 512
    private val TEXTURE = ECRModIDs.guiLocation("mim/dependency_tree")
    val PANEL = Region(0, 0, 256, 256)
    val CLOSE = Region(256, 227, 17, 17)
    val CLOSE_PRESSED = Region(273, 227, 17, 17)
    val CLOSE_HOVERED = Region(290, 227, 17, 17)
    val BUTTON = Region(256, 244, 17, 17)
    val BUTTON_PRESSED = Region(273, 244, 17, 17)
    val BUTTON_HOVERED = Region(290, 244, 17, 17)
    val CRAFT = Region(256, 261, 64, 16)
    val CRAFT_PRESSED = Region(320, 261, 64, 16)
    val CRAFT_HOVERED = Region(384, 261, 64, 16)
    val STOP = Region(256, 277, 32, 16)
    val STOP_PRESSED = Region(288, 277, 32, 16)
    val STOP_HOVERED = Region(320, 277, 32, 16)
    val UP = Region(256, 0, 13, 13)
    val UP_PRESSED = Region(269, 0, 13, 13)
    val UP_HOVERED = Region(282, 0, 13, 13)
    val DOWN = Region(256, 13, 13, 13)
    val DOWN_PRESSED = Region(269, 13, 13, 13)
    val DOWN_HOVERED = Region(282, 13, 13, 13)
    val THUMB = Region(256, 26, 13, 32)
    val THUMB_PRESSED = Region(269, 26, 13, 32)
    val THUMB_HOVERED = Region(282, 26, 13, 32)
    val TRACK = Region(256, 78, 13, 149)
    val SLOT = Region(256, 58, 20, 20)
    val CARD = Region(256, 293, 96, 32)
    val QUANTITY = Region(256, 325, 32, 17)
    private val STORED = Region(0, 256, 8, 8)
    private val CRAFTABLE = Region(8, 256, 8, 8)
    private val MISSING = Region(16, 256, 8, 8)
    private val CYCLE = Region(24, 256, 8, 8)

    class Region(val u: Int, val v: Int, val width: Int, val height: Int) {
        fun draw(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int = this.width, height: Int = this.height) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, u.toFloat(), v.toFloat(),
                width, height, this.width, this.height, TEXTURE_SIZE, TEXTURE_SIZE)
        }

        fun frame(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int) {
            require(width >= 4) { "A framed region must fit its borders" }
            drawPart(graphics, x, y, u, 2, 2)
            drawPart(graphics, x + 2, y, u + 2, width - 4, this.width - 4)
            drawPart(graphics, x + width - 2, y, u + this.width - 2, 2, 2)
        }

        private fun drawPart(graphics: GuiGraphicsExtractor, x: Int, y: Int, source: Int, width: Int, sourceWidth: Int) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, x, y, source.toFloat(), v.toFloat(),
                width, height, sourceWidth, height, TEXTURE_SIZE, TEXTURE_SIZE)
        }
    }

    fun status(value: MIMDependencyStatus): Region = when (value) {
        MIMDependencyStatus.STORED -> STORED
        MIMDependencyStatus.CRAFTABLE -> CRAFTABLE
        MIMDependencyStatus.MISSING -> MISSING
        MIMDependencyStatus.CYCLE, MIMDependencyStatus.LIMIT -> CYCLE
    }
}

internal class MIMDependencyButton(
    private val font: Font, label: Component,
    private val normal: MIMDependencyTextures.Region, private val pressed: MIMDependencyTextures.Region,
    private val hovered: MIMDependencyTextures.Region, private val showLabel: Boolean = true,
    action: () -> Unit
) : MIMPressedButton(0, 0, normal.width, normal.height, label, action) {
    var textScale = 1f

    override fun extractContents(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        val texture = when {
            !active -> normal
            isPressed -> pressed
            isHovered -> hovered
            else -> normal
        }
        texture.draw(graphics, x, y, width, height)
        if (!showLabel) return
        val scale = minOf(textScale, (width - 4 * textScale) / font.width(message).coerceAtLeast(1))
        graphics.pose().pushMatrix()
        graphics.pose().translate(x + width / 2f, y + height / 2f)
        graphics.pose().scale(scale, scale)
        graphics.text(font, message, -font.width(message) / 2, -font.lineHeight / 2,
            if (active) MIMGuiTextures.TEXT else 0xFF8B8B8B.toInt(), false)
        graphics.pose().popMatrix()
    }
}
