package com.algorithmlx.ecr.client.screen

import com.algorithmlx.ecr.api.client.MRULineAnimation
import com.algorithmlx.ecr.api.client.drawMRULine
import com.algorithmlx.ecr.client.widget.MithrilineFurnaceProgressArrow
import com.algorithmlx.ecr.common.block.entity.MithrilineFurnaceEntity
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.common.menu.MithrilineFurnaceMenu
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import java.awt.Color

class MithrilineFurnaceScreen(
    menu: MithrilineFurnaceMenu,
    inv: Inventory,
    title: Component
): AbstractContainerScreen<MithrilineFurnaceMenu>(menu, inv, title) {
    private val mruAnimation = MRULineAnimation()

    override fun init() {
        super.init()
        addRenderableOnly(MithrilineFurnaceProgressArrow(this.leftPos + 84, this.topPos + 41, this.menu))
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

        val be = menu.blockEntity
        if (be is MithrilineFurnaceEntity) {
            val mru = be.mruStorage
            drawMRULine(
                graphics, mru,
                8, 60,
                leftPos, topPos,
                16, 16,
                mouseX, mouseY,
                COLOR, COLOR,
                animation = mruAnimation,
                deltaTicks = deltaTicks
            )
        }
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, xm: Int, ym: Int) {}

    companion object {
        private val TEXTURE = ECRModIDs.guiLocation(ECRModIDs.MITHRILINE_FURNACE)
        private val COLOR = Color(113, 178, 123, 135).rgb
    }
}
