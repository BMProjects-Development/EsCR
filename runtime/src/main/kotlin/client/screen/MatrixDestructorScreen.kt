package com.algorithmlx.ecr.client.screen

import com.algorithmlx.ecr.api.client.MRULineAnimation
import com.algorithmlx.ecr.api.client.drawMRULine
import com.algorithmlx.ecr.common.block.entity.MatrixDestructorEntity
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.common.menu.MatrixDestructorMenu
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

class MatrixDestructorScreen(
    menu: MatrixDestructorMenu,
    inv: Inventory,
    title: Component
): AbstractContainerScreen<MatrixDestructorMenu>(menu, inv, title) {
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

        val be = menu.blockEntity as? MatrixDestructorEntity ?: return
        val mru = be.mruStorage
        val status = be.status

        drawMRULine(
            graphics, mru, 37, 17, this.leftPos, this.topPos, 102, 10, mouseX, mouseY,
            animation = mruAnimation,
            deltaTicks = deltaTicks
        )

        val statusX = when (status) {
            MatrixDestructorEntity.MatrixDestructorStatus.WORKING -> 10F
            MatrixDestructorEntity.MatrixDestructorStatus.STOPPED -> 10F
            else -> 0F
        }

        val statusY = when (status) {
            MatrixDestructorEntity.MatrixDestructorStatus.WARNING -> 10F
            MatrixDestructorEntity.MatrixDestructorStatus.STOPPED -> 10F
            else -> 0F
        }

        graphics.blit(
            RenderPipelines.GUI_TEXTURED,
            INDICATOR_TEXTURE,
            this.leftPos + 83, this.topPos + 36,
            statusX, statusY,
            10, 10,
            20, 20
        )
    }

    override fun extractLabels(graphics: GuiGraphicsExtractor, xm: Int, ym: Int) {}

    companion object {
        private val TEXTURE = ECRModIDs.guiLocation(ECRModIDs.MATRIX_DESTRUCTOR)
        private val INDICATOR_TEXTURE = ECRModIDs.guiLocation("widget/${ECRModIDs.MATRIX_DESTRUCTOR}_indicators")
    }
}
