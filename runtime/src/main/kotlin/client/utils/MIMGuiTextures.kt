package com.algorithmlx.ecr.client.utils

import com.algorithmlx.ecr.common.menu.MIMConfigurationLayout
import com.algorithmlx.ecr.common.menu.MIMTerminalLayout
import com.algorithmlx.ecr.init.ECRModIDs
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.resources.Identifier

internal object MIMGuiTextures {
    const val TEXT = 0xFF404040.toInt()

    private val MANAGER = ECRModIDs.guiLocation("mim/manager")
    private val EDITOR = Identifier.withDefaultNamespace("recipe_book/overlay_recipe")
    private val SLOT = Identifier.withDefaultNamespace("container/slot")
    val TERMINAL_SCROLLBAR = ScrollThumb(MANAGER, 215, 227)

    class ScrollThumb(private val texture: Identifier, private val normalU: Int, private val disabledU: Int) {
        fun draw(graphics: GuiGraphicsExtractor, x: Int, y: Int, height: Int, active: Boolean) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, (if (active) normalU else disabledU).toFloat(), 0f,
                12, height, 12, 15, 256, 256)
        }
    }

    fun scrollThumb(layout: MIMConfigurationLayout, bar: MIMConfigurationLayout.ScrollBar): ScrollThumb =
        ScrollThumb(ECRModIDs.guiLocation(layout.texture), bar.thumbU, bar.disabledThumbU)

    fun configuration(graphics: GuiGraphicsExtractor, x: Int, y: Int, layout: MIMConfigurationLayout) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, ECRModIDs.guiLocation(layout.texture), x, y, 0f, 0f,
            layout.width, layout.height, 256, 256)
    }

    fun terminal(graphics: GuiGraphicsExtractor, x: Int, y: Int, rows: Int, available: Boolean) {
        val interiorY = MIMTerminalLayout.GRID_Y
        val interiorHeight = MIMTerminalLayout.scrollHeight(MIMTerminalLayout.BASE_ROWS)
        val bottomY = interiorY + interiorHeight
        val displayedHeight = MIMTerminalLayout.scrollHeight(rows)

        managerRegion(graphics, x, y, MIMTerminalLayout.WIDTH, interiorY, 0, 0)
        managerRegion(graphics, x, y + interiorY, MIMTerminalLayout.WIDTH, displayedHeight,
            0, interiorY, MIMTerminalLayout.WIDTH, interiorHeight)
        managerRegion(graphics, x, y + interiorY + displayedHeight, MIMTerminalLayout.WIDTH,
            MIMTerminalLayout.BASE_HEIGHT - bottomY, 0, bottomY)
        if (!available)
            managerRegion(graphics, x + MIMTerminalLayout.STORAGE_X, y + interiorY - 1,
                MIMTerminalLayout.STORAGE_WIDTH, displayedHeight + 2, 0, MIMTerminalLayout.BASE_HEIGHT,
                MIMTerminalLayout.STORAGE_WIDTH, MIMTerminalLayout.PATCH_HEIGHT)
    }

    fun editor(graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EDITOR, x, y, width, height)
    }

    fun slot(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, x - 1, y - 1, 18, 18)
    }

    fun craftingButton(graphics: GuiGraphicsExtractor, x: Int, y: Int) {
        managerRegion(graphics, x, y, MIMTerminalLayout.CRAFT_BUTTON_WIDTH, MIMTerminalLayout.CRAFT_BUTTON_HEIGHT, 194, 0)
    }

    private fun managerRegion(
        graphics: GuiGraphicsExtractor, x: Int, y: Int, width: Int, height: Int,
        u: Int, v: Int, sourceWidth: Int = width, sourceHeight: Int = height
    ) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, MANAGER, x, y, u.toFloat(), v.toFloat(),
            width, height, sourceWidth, sourceHeight, 256, 256)
    }
}
