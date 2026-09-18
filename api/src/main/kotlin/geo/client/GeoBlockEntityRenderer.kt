package com.algorithmlx.ecr.api.geo.client

import com.algorithmlx.ecr.api.geo.GeoAnimatable
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.phys.Vec3

open class GeoBlockEntityRenderState : BlockEntityRenderState() {
    var geo: BedrockGeoRenderData? = null
    var modelYRotation = 0F
}

open class GeoBlockEntityRenderer<T>(
    @Suppress("UNUSED_PARAMETER") context: BlockEntityRendererProvider.Context
) : BlockEntityRenderer<T, GeoBlockEntityRenderState>
    where T : BlockEntity, T : GeoAnimatable {

    override fun createRenderState() = GeoBlockEntityRenderState()

    override fun extractRenderState(
        blockEntity: T,
        state: GeoBlockEntityRenderState,
        partialTick: Float,
        cameraPosition: Vec3,
        breakProgress: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(blockEntity, state, partialTick, cameraPosition, breakProgress)
        state.modelYRotation = 0F
        val now = ClientGeoAnimations.clientTimeSeconds(partialTick)
        val molang = blockEntity.geoMolangContext(partialTick)
        val model = blockEntity.geoModel
        state.geo = BedrockGeoRenderEngine.extract(
            model,
            ClientGeoAnimations.snapshot(blockEntity.geoAnimationState, molang, now),
            molang,
            now
        )
        if (model.blockRotation.enabled) {
            val facing = blockEntity.blockState
                .getOptionalValue(HorizontalDirectionalBlock.FACING)
                .orElse(null)
                ?.let { direction -> if (model.blockRotation.opposite) direction.opposite else direction }
            state.modelYRotation = facing?.let(BedrockGeoRenderEngine::horizontalRotationDegrees) ?: 0F
        }
    }

    override fun submit(
        state: GeoBlockEntityRenderState,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        camera: CameraRenderState
    ) {
        val geo = state.geo ?: return
        poseStack.pushPose()
        poseStack.translate(0.5, 0.0, 0.5)
        if (state.modelYRotation != 0F) {
            poseStack.rotateDegrees(Axis.YP, state.modelYRotation)
        }
        BedrockGeoRenderEngine.submit(geo, poseStack, collector, state.lightCoords)
        poseStack.popPose()
    }
}
