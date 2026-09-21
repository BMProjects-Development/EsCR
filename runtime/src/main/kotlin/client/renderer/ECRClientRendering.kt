package com.algorithmlx.ecr.client.renderer

import com.algorithmlx.ecr.api.client.render.MultiblockWorldPreview
import com.algorithmlx.ecr.api.particle.ClientParticleSystems
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.LevelRenderState

object ECRClientRendering {
    fun submit(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        levelRenderState: LevelRenderState
    ) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        ClientParticleSystems.get(level)?.submit(
            poseStack,
            collector,
            levelRenderState,
            minecraft.deltaTracker.getGameTimeDeltaPartialTick(false),
            minecraft.player?.uuid,
            minecraft.options.cameraType.isFirstPerson
        )
        BoundGemLinkRenderer.submit(poseStack, collector, levelRenderState)
        MultiblockWorldPreview.submit(poseStack, collector, levelRenderState)
        MagicShieldRenderer.submit(poseStack, collector, levelRenderState)
        MRULinkRenderer.submit(poseStack, collector, levelRenderState)
    }
}
