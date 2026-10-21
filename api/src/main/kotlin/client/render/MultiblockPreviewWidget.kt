package com.algorithmlx.ecr.api.client.render

import com.algorithmlx.ecr.api.multiblock.Multiblock
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.narration.NarratedElementType
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.network.chat.Component

class MultiblockPreviewWidget(
    x: Int,
    y: Int,
    width: Int,
    height: Int,
    var multiblock: Multiblock,
    var transform: MultiblockPreviewTransform = MultiblockPreviewTransform(),
    message: Component = Component.empty()
) : AbstractWidget(x, y, width, height, message) {
    override fun extractWidgetRenderState(
        graphics: GuiGraphicsExtractor,
        mouseX: Int,
        mouseY: Int,
        partialTick: Float
    ) {
        MultiblockPreviewGuiBridge.add(
            graphics,
            MultiblockPreviewRenderState(
                multiblock,
                transform = transform,
                x0 = x,
                y0 = y,
                x1 = right,
                y1 = bottom
            )
        )
    }

    override fun updateWidgetNarration(output: NarrationElementOutput) {
        output.add(NarratedElementType.TITLE, message)
    }
}
