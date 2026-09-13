package com.algorithmlx.ecr.api.client.render

import com.mojang.blaze3d.platform.Lighting
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher
import net.minecraft.client.renderer.state.gui.GuiRenderState

class MultiblockPreviewPictureRenderer: PictureInPictureRenderer<MultiblockPreviewRenderState>() {
    private val renderers = object : LinkedHashMap<Any, SinglePreviewRenderer>(16, 0.75F, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, SinglePreviewRenderer>): Boolean {
            if (size <= MAX_RENDERERS) return false
            eldest.value.close()
            return true
        }
    }

    override fun getRenderStateClass(): Class<MultiblockPreviewRenderState> = MultiblockPreviewRenderState::class.java

    override fun prepare(
        state: MultiblockPreviewRenderState,
        guiRenderState: GuiRenderState,
        featureRenderDispatcher: FeatureRenderDispatcher,
        guiScale: Int
    ) {
        renderers.getOrPut(state.textureKey) { SinglePreviewRenderer() }
            .prepare(state, guiRenderState, featureRenderDispatcher, guiScale)
    }

    override fun renderToTexture(
        state: MultiblockPreviewRenderState,
        poseStack: PoseStack,
        submitter: SubmitNodeCollector
    ) = throw AssertionError("Multiblock previews are rendered by keyed child renderers")

    override fun getTextureLabel(): String = "multiblock_preview"

    override fun close() {
        renderers.values.forEach(SinglePreviewRenderer::close)
        renderers.clear()
        super.close()
    }

    private class SinglePreviewRenderer(
        private val previewRenderer: MultiblockPreviewRenderer = MultiblockPreviewRenderer()
    ): PictureInPictureRenderer<MultiblockPreviewRenderState>() {
        override fun getRenderStateClass(): Class<MultiblockPreviewRenderState> =
            MultiblockPreviewRenderState::class.java

        override fun renderToTexture(
            state: MultiblockPreviewRenderState,
            poseStack: PoseStack,
            submitter: SubmitNodeCollector
        ) {
            // Block models need the same directional lighting as 3D block items.
            // Without it, every face receives nearly identical light and the model looks flat.
            Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D)

            val bounds = MultiblockPreviewBounds(
                x = -(state.x1() - state.x0()) / 2f,
                y = -(state.y1() - state.y0()) / 2f,
                width = (state.x1() - state.x0()).toFloat(),
                height = (state.y1() - state.y0()).toFloat()
            )

            when (val model = state.model) {
                is MultiblockPreviewModel.Pattern -> previewRenderer.submit(
                    model.multiblock, poseStack, submitter, bounds, state.transform
                )
                is MultiblockPreviewModel.Assembled -> previewRenderer.submit(
                    model.definition, model.assembled, poseStack, submitter, bounds, state.transform
                )
            }
        }

        override fun getTextureLabel(): String = "multiblock_preview"

        override fun getTranslateY(height: Int, scale: Int): Float = height / 2f
    }

    private companion object {
        const val MAX_RENDERERS = 32
    }
}
