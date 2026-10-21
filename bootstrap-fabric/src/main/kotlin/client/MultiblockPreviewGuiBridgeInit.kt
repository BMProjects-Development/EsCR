package com.algorithmlx.ecr.fabric.client

import com.algorithmlx.ecr.api.client.render.MultiblockPreviewGuiBridge
import com.algorithmlx.ecr.api.client.render.MultiblockPreviewPictureRenderer
import com.algorithmlx.ecr.api.client.render.MultiblockPreviewRenderState
import com.algorithmlx.ecr.api.mixin.client.GameRendererAccessor
import com.algorithmlx.ecr.api.mixin.client.GuiGraphicsExtractorAccessor
import com.algorithmlx.ecr.fabric.mixin.GuiRendererAccessor
import net.minecraft.client.Minecraft

object MultiblockPreviewGuiBridgeInit {
    fun init() {
        MultiblockPreviewGuiBridge.install { extractor, state ->
            ensureRendererRegistered()
            (extractor as GuiGraphicsExtractorAccessor).guiRenderState().addPicturesInPictureState(state)
        }
    }

    private fun ensureRendererRegistered() {
        val guiRenderer = (Minecraft.getInstance().gameRenderer as GameRendererAccessor).guiRenderer()
        val pipRenderers = (guiRenderer as GuiRendererAccessor).pictureInPictureRenderers()

        pipRenderers.putIfAbsent(MultiblockPreviewRenderState::class.java, MultiblockPreviewPictureRenderer())
    }
}
