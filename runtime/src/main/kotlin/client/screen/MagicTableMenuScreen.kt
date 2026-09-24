package com.algorithmlx.ecr.client.screen

import com.algorithmlx.ecr.api.client.MRULineAnimation
import com.algorithmlx.ecr.api.client.drawMRULine
import com.algorithmlx.ecr.client.widget.MagicTableProgressArrow
import com.algorithmlx.ecr.common.block.entity.MagicTableBlockEntity
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.common.menu.MagicTableMenu
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class MagicTableMenuScreen(
    menu: MagicTableMenu,
    inv: Inventory,
    title: Component
): AbstractContainerScreen<MagicTableMenu>(menu, inv, title) {
    private val mruAnimation = MRULineAnimation()

    override fun init() {
        super.init()
        addRenderableOnly(MagicTableProgressArrow(this.leftPos + 85, this.topPos + 39, this.menu))
    }

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

        val be = menu.blockEntity as? MagicTableBlockEntity ?: return
        val mru = be.mruStorage
        drawMRULine(
            graphics, mru,
            98, 17,
            this.leftPos, this.topPos,
            52, 8,
            mouseX, mouseY,
            animation = mruAnimation,
            deltaTicks = deltaTicks
        )
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, xm: Int, ym: Int) {}

    companion object {
        private val TEXTURE = ECRModIDs.guiLocation(ECRModIDs.MAGIC_TABLE)
    }
}
