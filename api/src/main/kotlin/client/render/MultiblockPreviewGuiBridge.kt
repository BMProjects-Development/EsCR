package com.algorithmlx.ecr.api.client.render

import net.minecraft.client.gui.GuiGraphicsExtractor

object MultiblockPreviewGuiBridge {
    private lateinit var implementation: (GuiGraphicsExtractor, MultiblockPreviewRenderState) -> Unit

    fun install(implement: (GuiGraphicsExtractor, MultiblockPreviewRenderState) -> Unit) {
        check(!this::implementation.isInitialized) { "Already initialized" }
        this.implementation = implement
    }

    fun add(graphics: GuiGraphicsExtractor, state: MultiblockPreviewRenderState) =
        implementation(graphics, state)
}
