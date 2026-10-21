package com.algorithmlx.ecr.client.renderer

import com.algorithmlx.ecr.common.block.entity.MatrixDestructorEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import it.unimi.dsi.fastutil.HashCommon
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.item.ItemStackRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.Vec3
import kotlin.math.sin

class MatrixDestructorRenderState: BlockEntityRenderState() {
    var renderStack = ItemStack.EMPTY
    var renderState = ItemStackRenderState()
    var animationTicks = 0.0
}

class MatrixDestructorRenderer(
    private val ctx: BlockEntityRendererProvider.Context
): BlockEntityRenderer<MatrixDestructorEntity, MatrixDestructorRenderState> {
    override fun createRenderState(): MatrixDestructorRenderState = MatrixDestructorRenderState()

    override fun extractRenderState(
        blockEntity: MatrixDestructorEntity,
        state: MatrixDestructorRenderState,
        partialTicks: Float,
        cameraPosition: Vec3,
        breakProgress: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress)

        state.renderStack = blockEntity.getItem(0)
        state.animationTicks = (blockEntity.level?.gameTime ?: 0L) + partialTicks.toDouble()

        val seed = HashCommon.long2int(state.blockPos.asLong())
        state.renderState.clear()
        ctx.itemModelResolver().appendItemLayers(state.renderState, state.renderStack, ItemDisplayContext.FIXED, blockEntity.level, null, seed)
    }

    override fun submit(
        state: MatrixDestructorRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        camera: CameraRenderState
    ) {
        poseStack.pushPose()
        poseStack.translate(0.5, 0.85, 0.5)

        val scale = if (state.renderStack.item is BlockItem) 0.55F else 0.35F
        poseStack.scale(scale, scale, scale)

        val animationTicks = state.animationTicks
        poseStack.translate(0.0, sin(animationTicks * BOB_RADIANS_PER_TICK) * BOB_AMPLITUDE, 0.0)
        poseStack.rotateDegrees(Axis.YP, ((animationTicks * ROTATION_DEGREES_PER_TICK) % 360.0).toFloat())

        state.renderState.submit(poseStack, submitNodeCollector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0)

        poseStack.popPose()
    }

    companion object {
        private const val BOB_AMPLITUDE = 0.072
        private const val BOB_RADIANS_PER_TICK = 1.0 / 16.0
        private const val ROTATION_DEGREES_PER_TICK = 12.5 / 16.0
    }
}


