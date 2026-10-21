package com.algorithmlx.ecr.client.renderer

import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.common.block.entity.MithrilineFurnaceEntity
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import net.minecraft.client.model.geom.ModelLayerLocation
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.model.geom.PartPose
import net.minecraft.client.model.geom.builders.CubeDeformation
import net.minecraft.client.model.geom.builders.CubeListBuilder
import net.minecraft.client.model.geom.builders.LayerDefinition
import net.minecraft.client.model.geom.builders.MeshDefinition
import net.minecraft.client.renderer.Sheets
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.client.renderer.texture.TextureAtlasSprite
import net.minecraft.client.resources.model.sprite.SpriteId
import net.minecraft.world.phys.Vec3

class MithrilineFurnaceRenderState: BlockEntityRenderState() {
    var coreRotation: Float = 0F
}

class MithrilineFurnaceRenderer(
    ctx: BlockEntityRendererProvider.Context
): BlockEntityRenderer<MithrilineFurnaceEntity, MithrilineFurnaceRenderState> {
    private val body: ModelPart
    private val sprite: TextureAtlasSprite

    init {
        val mp = ctx.bakeLayer(MF_LAYER)
        body = mp.getChild("core").also {
            it.xRot = 0F
            it.yRot = 0F
            it.zRot = 0F
        }
        sprite = ctx.sprites().get(MF_MATERIAL)
    }

    override fun createRenderState(): MithrilineFurnaceRenderState = MithrilineFurnaceRenderState()

    override fun extractRenderState(
        blockEntity: MithrilineFurnaceEntity,
        state: MithrilineFurnaceRenderState,
        partialTicks: Float,
        cameraPosition: Vec3,
        breakProgress: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress)
        state.coreRotation = blockEntity.coreRotationPrevious + (blockEntity.coreRotationAngle - blockEntity.coreRotationPrevious) * partialTicks
    }

    override fun submit(
        state: MithrilineFurnaceRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        camera: CameraRenderState
    ) {
        poseStack.pushPose()
        poseStack.translate(0.5, 0.5, 0.5)

        poseStack.rotateDegrees(Axis.YP, state.coreRotation)

        submitNodeCollector.submitModelPart(
            body, poseStack, RenderTypes.entityCutout(MF_MATERIAL.atlasLocation()),
            state.lightCoords, OverlayTexture.NO_OVERLAY, sprite
        )

        poseStack.popPose()
    }

    companion object {
        @JvmField val MF_LAYER = ModelLayerLocation("mithriline_furnace".ecRL, "core")
        @JvmField val MF_MATERIAL = SpriteId(Sheets.BLOCKS_MAPPER.sheet(), "block/mithriline_furnace".ecRL)

        @JvmStatic
        fun createBodyLayer(): LayerDefinition {
            val meshDefinition = MeshDefinition().also { definition ->
                definition.root.addOrReplaceChild(
                    "core",
                    CubeListBuilder.create().texOffs(90, 8).addBox(2.0f, 2.0f, -2.0f, 2.0f, 2.0f, 4.0f, CubeDeformation(0.0f))
                        .texOffs(90, 2).addBox(-4.0f, 2.0f, -2.0f, 2.0f, 2.0f, 4.0f, CubeDeformation(0.0f))
                        .texOffs(98, 8).addBox(-2.0f, 2.0f, 2.0f, 4.0f, 2.0f, 2.0f, CubeDeformation(0.0f))
                        .texOffs(82, 6).addBox(2.0f, -4.0f, -2.0f, 2.0f, 2.0f, 4.0f, CubeDeformation(0.0f))
                        .texOffs(82, 0).addBox(-4.0f, -4.0f, -2.0f, 2.0f, 2.0f, 4.0f, CubeDeformation(0.0f))
                        .texOffs(98, 0).addBox(-2.0f, -4.0f, 2.0f, 4.0f, 2.0f, 2.0f, CubeDeformation(0.0f))
                        .texOffs(94, 14).addBox(-2.0f, 2.0f, -4.0f, 4.0f, 2.0f, 2.0f, CubeDeformation(0.0f))
                        .texOffs(82, 14).addBox(-2.0f, -4.0f, -4.0f, 4.0f, 2.0f, 2.0f, CubeDeformation(0.0f))
                        .texOffs(104, 22).addBox(-4.0f, -2.0f, -4.0f, 2.0f, 4.0f, 2.0f, CubeDeformation(0.0f))
                        .texOffs(98, 18).addBox(-4.0f, -2.0f, 2.0f, 2.0f, 4.0f, 2.0f, CubeDeformation(0.0f))
                        .texOffs(82, 18).addBox(2.0f, -2.0f, -4.0f, 2.0f, 4.0f, 2.0f, CubeDeformation(0.0f))
                        .texOffs(90, 18).addBox(2.0f, -2.0f, 2.0f, 2.0f, 4.0f, 2.0f, CubeDeformation(0.0f)),
                    PartPose.ZERO
                )
            }

            return LayerDefinition.create(meshDefinition, 128, 128)
        }
    }
}
