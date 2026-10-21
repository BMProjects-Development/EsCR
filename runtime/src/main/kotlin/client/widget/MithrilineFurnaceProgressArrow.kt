package com.algorithmlx.ecr.client.widget

import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.common.menu.MithrilineFurnaceMenu
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import kotlin.math.floor

class MithrilineFurnaceProgressArrow(
    x: Int, y: Int, private val menu: MithrilineFurnaceMenu
): AbstractWidget(x, y, 16, 8, Component.empty()) {
    override fun extractWidgetRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        a: Float
    ) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.x, this.y, 0f, 0f, 8, 16, 16, 16)
        renderProgressArrow(graphics)
    }

    private fun renderProgressArrow(graphics: GuiGraphicsExtractor) {
        val progress = this.menu.data.get(0)
        val maxProgress = this.menu.data.get(1)

        if (maxProgress > 0) {
            val calc = progress.toFloat() / maxProgress.toFloat()
            val floor = floor(calc * 16)

            graphics.blit(
                RenderPipelines.GUI_TEXTURED, TEXTURE,
                this.x, (this.y + (16 - floor)).toInt(), 8F, 16F - floor, 8, floor.toInt(), 16, 16
            )
        }
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {}

    companion object {
        private val TEXTURE = ECRModIDs.guiLocation("widget/${ECRModIDs.MITHRILINE_FURNACE}_arrow")
    }
}
