package com.algorithmlx.ecr.client.screen

import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.common.menu.EnrichmentChamberReceiverMenu
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class EnrichmentChamberReceiverScreen(
    menu: EnrichmentChamberReceiverMenu,
    inventory: Inventory,
    title: Component
): AbstractContainerScreen<EnrichmentChamberReceiverMenu>(menu, inventory, title) {
    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
        super.extractBackground(graphics, mouseX, mouseY, a)
        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            TEXTURE,
            this.leftPos, this.topPos,
            0F, 0F,
            this.imageWidth, this.imageHeight,
            256, 256
        )
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, xm: Int, ym: Int) {}

    companion object {
        private val TEXTURE = ECRModIDs.guiLocation("single_slot_gui")
    }
}