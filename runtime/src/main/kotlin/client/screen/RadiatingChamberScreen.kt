package com.algorithmlx.ecr.client.screen

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.client.MRULineAnimation
import com.algorithmlx.ecr.api.client.drawMRULine
import com.algorithmlx.ecr.common.block.entity.RadiatingChamberEntity
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.common.menu.RadiatingChamberMenu
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import java.util.Locale

class RadiatingChamberScreen(
    menu: RadiatingChamberMenu,
    inv: Inventory,
    title: Component
): AbstractContainerScreen<RadiatingChamberMenu>(menu, inv, title) {
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

        val be = this.menu.blockEntity as? RadiatingChamberEntity ?: return
        drawMRULine(
            graphics, be.mruStorage,
            26, 12,
            this.leftPos, this.topPos,
            124, 8,
            mouseX, mouseY,
            animation = this.mruAnimation,
            deltaTicks = deltaTicks
        )

        drawBalance(graphics, "upper_balance", be.balance.upperBalance, 30)
        drawBalance(graphics, "lower_balance", be.balance.lowerBalance, 66)
    }

    private fun drawBalance(graphics: GuiGraphicsExtractor, key: String, balance: Double, y: Int) {
        val text = Component.translatable("screen.$ModId.radiating_chamber.$key", String.format(Locale.ROOT, "%.3f", balance))
        graphics.text(this.font, text, this.leftPos + 75, this.topPos + y, TEXT_COLOR, false)
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int) {}

    companion object {
        private val TEXTURE = ECRModIDs.guiLocation(ECRModIDs.RADIATING_CHAMBER)
        private const val TEXT_COLOR = 0xFF404040.toInt()
    }
}
