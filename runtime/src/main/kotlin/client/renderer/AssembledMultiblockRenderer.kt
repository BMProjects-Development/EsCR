package com.algorithmlx.ecr.client.renderer

import com.algorithmlx.ecr.api.geo.client.BedrockGeoRenderData
import com.algorithmlx.ecr.api.geo.client.BedrockGeoRenderEngine
import com.algorithmlx.ecr.api.geo.client.ClientGeoAnimations
import com.algorithmlx.ecr.api.geo.GeoAnimatable
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDefinition
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockPartEntity
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.Vec3

class AssembledMultiblockRenderState: BlockEntityRenderState() {
    var model: BedrockGeoRenderData? = null
    var modelYRotation = 0F
    var anchorX: Float = 0F
    var anchorY: Float = 0F
    var anchorZ: Float = 0F
    var modelLight: Int = 0
}

class AssembledMultiblockRenderer<T : BlockEntity>(
    context: BlockEntityRendererProvider.Context
): BlockEntityRenderer<T, AssembledMultiblockRenderState> {
    override fun createRenderState() = AssembledMultiblockRenderState()

    override fun extractRenderState(
        blockEntity: T,
        state: AssembledMultiblockRenderState,
        partialTick: Float,
        cameraPosition: Vec3,
        breakProgress: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(blockEntity, state, partialTick, cameraPosition, null)
        state.model = null
        state.modelYRotation = 0F
        val partEntity = blockEntity as? AssembledMultiblockPartEntity ?: return
        val partData = partEntity.assembledMultiblockData ?: return
        if (partData.controllerPos != blockEntity.blockPos) return
        val definition = MultiblockDefinitions.assembled(partData.definitionId) ?: return
        val model = definition.formedModel ?: return
        val animatable = blockEntity as? GeoAnimatable ?: return
        val modelFacing = if (model.blockRotation.opposite) partData.facing.opposite else partData.facing
        state.modelYRotation = if (model.blockRotation.enabled) {
            BedrockGeoRenderEngine.horizontalRotationDegrees(modelFacing)
        } else {
            0F
        }
        val anchor = AssembledMultiblockDefinition.rotate(definition.formedModelAnchor, partData.facing)
            ?: return
        state.anchorX = anchor.x.toFloat()
        state.anchorY = anchor.y.toFloat()
        state.anchorZ = anchor.z.toFloat()
        state.modelLight = blockEntity.level?.let { level ->
            val anchorPos = blockEntity.blockPos.offset(anchor)
            if (level.isLoaded(anchorPos)) LightCoordsUtil.getLightCoords(level, anchorPos) else state.lightCoords
        } ?: state.lightCoords
        val now = ClientGeoAnimations.clientTimeSeconds(partialTick)
        val molang = animatable.geoMolangContext(partialTick)
        state.model = BedrockGeoRenderEngine.extract(
            model,
            ClientGeoAnimations.snapshot(animatable.geoAnimationState, molang, now),
            molang,
            now
        )
    }

    override fun submit(
        state: AssembledMultiblockRenderState,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        camera: CameraRenderState
    ) {
        val model = state.model ?: return
        poseStack.pushPose()
        poseStack.translate(
            state.anchorX.toDouble() + 0.5,
            state.anchorY.toDouble(),
            state.anchorZ.toDouble() + 0.5
        )
        if (state.modelYRotation != 0F) {
            poseStack.rotateDegrees(Axis.YP, state.modelYRotation)
        }
        BedrockGeoRenderEngine.submit(model, poseStack, collector, state.modelLight)
        poseStack.popPose()
    }

    override fun shouldRenderOffScreen(): Boolean = true

    override fun getViewDistance(): Int = 128
}
