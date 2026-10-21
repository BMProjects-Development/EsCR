package com.algorithmlx.ecr.client.screen

import com.algorithmlx.ecr.api.client.MRULineAnimation
import com.algorithmlx.ecr.api.client.drawMRULine
import com.algorithmlx.ecr.common.block.entity.enrichment.EnrichmentChamberControllerEntity
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.common.menu.EnrichmentChamberControllerMenu
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class EnrichmentChamberControllerScreen(
    menu: EnrichmentChamberControllerMenu,
    inventory: Inventory,
    component: Component
): AbstractContainerScreen<EnrichmentChamberControllerMenu>(menu, inventory, component) {
    private val mruAnimation = MRULineAnimation()

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, deltaTicks: Float) {
        super.extractBackground(graphics, mouseX, mouseY, deltaTicks)
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            TEXTURE,
            this.leftPos, this.topPos,
            0F, 0F,
            this.imageWidth, this.imageHeight,
            256, 256
        )

        val be = menu.blockEntity as? EnrichmentChamberControllerEntity ?: return
        val mru = be.mruStorage
        drawMRULine(
            graphics, mru,
            26, 39,
            this.leftPos, this.topPos,
            124, 8,
            mouseX, mouseY,
            animation = mruAnimation,
            deltaTicks = deltaTicks
        )
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, xm: Int, ym: Int) {}

    companion object {
        private val TEXTURE = ECRModIDs.guiLocation("mru_line_gui")
    }
}
