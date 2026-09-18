package com.algorithmlx.ecr.api.geo.client

import com.algorithmlx.ecr.api.geo.GeoAnimatable
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.entity.EntityRenderer
import net.minecraft.client.renderer.entity.EntityRendererProvider
import net.minecraft.client.renderer.entity.state.EntityRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.entity.Entity

open class GeoEntityRenderState: EntityRenderState() {
    var geo: BedrockGeoRenderData? = null
    var bodyYaw = 0F
}

open class GeoEntityRenderer<T>(
    context: EntityRendererProvider.Context
): EntityRenderer<T, GeoEntityRenderState>(context) where T : Entity, T : GeoAnimatable {
    override fun createRenderState() = GeoEntityRenderState()

    override fun extractRenderState(entity: T, state: GeoEntityRenderState, partialTick: Float) {
        super.extractRenderState(entity, state, partialTick)
        val now = ClientGeoAnimations.clientTimeSeconds(partialTick)
        state.bodyYaw = entity.getYRot(partialTick)
        val molang = entity.geoMolangContext(partialTick)
        state.geo = BedrockGeoRenderEngine.extract(
            entity.geoModel,
            ClientGeoAnimations.snapshot(entity.geoAnimationState, molang, now),
            molang,
            now
        )
    }

    override fun submit(
        state: GeoEntityRenderState,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        camera: CameraRenderState
    ) {
        state.geo?.let { geo ->
            poseStack.pushPose()
            poseStack.rotateDegrees(Axis.YP, 180F - state.bodyYaw)
            BedrockGeoRenderEngine.submit(geo, poseStack, collector, state.lightCoords)
            poseStack.popPose()
        }
        super.submit(state, poseStack, collector, camera)
    }

    override fun getShadowRadius(state: GeoEntityRenderState): Float =
        state.geo?.shadowRadius ?: super.getShadowRadius(state)
}
