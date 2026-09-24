package com.algorithmlx.ecr.client.screen

import com.algorithmlx.ecr.api.client.MRULineAnimation
import com.algorithmlx.ecr.api.client.drawMRULine
import com.algorithmlx.ecr.common.block.entity.MagicalTeleporterEntity
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.common.menu.MagicalTeleporterMenu
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class MagicalTeleporterScreen(
    menu: MagicalTeleporterMenu,
    inv: Inventory,
    title: Component
): AbstractContainerScreen<MagicalTeleporterMenu>(
    menu,
    inv,
    title
) {
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

        val be = menu.blockEntity
        if (be is MagicalTeleporterEntity) {
            val mru = be.mruStorage
            drawMRULine(
                graphics, mru,
                26, 28,
                leftPos, topPos,
                124, 8,
                mouseX, mouseY,
                animation = mruAnimation,
                deltaTicks = deltaTicks
            )
        }
    }

    companion object {
        private val TEXTURE = ECRModIDs.guiLocation(ECRModIDs.MAGICAL_TELEPORTER)
    }
}
