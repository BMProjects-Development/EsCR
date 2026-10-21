package com.algorithmlx.ecr.client.widget

import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.common.menu.MagicTableMenu
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component

class MagicTableProgressArrow(
    x: Int, y: Int,
    private val menu: MagicTableMenu
): AbstractWidget(x, y, 16, 8, Component.empty()) {
    override fun extractWidgetRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        a: Float
    ) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.x, this.y, 0F, 0F, 24, 8, 24, 16)
        renderProgressArrow(graphics)
    }

    private fun renderProgressArrow(graphics: GuiGraphicsExtractor) {
        val progress = this.menu.data.get(0)
        val maxProgress = this.menu.data.get(1)

        if (maxProgress <= 0) return
        val width = ((progress.toFloat() / maxProgress.toFloat()) * 24).toInt()
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.x, this.y, 0F, 8F, width, 8, 24, 16)
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {}

    companion object {
        private val TEXTURE = ECRModIDs.guiLocation("widget/${ECRModIDs.MAGIC_TABLE}_arrow")
    }
}
