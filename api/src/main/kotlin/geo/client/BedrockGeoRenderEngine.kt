package com.algorithmlx.ecr.api.geo.client

import com.algorithmlx.ecr.api.LOGGER
import com.algorithmlx.ecr.api.geo.GeoAnimationPlayback
import com.algorithmlx.ecr.api.geo.GeoLightMode
import com.algorithmlx.ecr.api.geo.GeoModel
import com.algorithmlx.ecr.api.geo.GeoRenderType
import com.algorithmlx.ecr.api.molang.runtime.MolangContext
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.LightCoordsUtil
import net.minecraft.core.Direction
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap

data class BedrockGeoRenderData(
    val model: BakedGeoModel,
    val pose: BedrockGeoPose,
    val texture: net.minecraft.resources.Identifier,
    val renderType: GeoRenderType,
    val scale: Float,
    val shadowRadius: Float,
    val lightMode: GeoLightMode
)

object BedrockGeoRenderEngine {
    private val loggedMissingGeometries = ConcurrentHashMap.newKeySet<String>()

    @JvmStatic
    @Volatile
    var gpuRenderingEnabled: Boolean = true

    fun extract(
        model: GeoModel,
        playbacks: List<GeoAnimationPlayback>,
        context: MolangContext,
        nowSeconds: Double
    ): BedrockGeoRenderData? {
        val baked = BedrockGeoAssets[model]
        if (baked == null) {
            val reference = model.geometryResource?.toString() ?: model.geometry
            if (loggedMissingGeometries.add(reference)) {
                val resourceCount = model.geometryResource?.let(BedrockGeoAssets::geometryCount) ?: 0
                if (resourceCount > 1) {
                    LOGGER.error(
                        "Unable to render GEO model: resource '{}' contains {} geometries; use a Bedrock geometry identifier",
                        reference,
                        resourceCount
                    )
                } else {
                    LOGGER.error("Unable to render GEO model: geometry '{}' is not loaded", reference)
                }
            }
            return null
        }
        return BedrockGeoRenderData(
            baked,
            BedrockGeoAnimator.pose(baked, playbacks, context, nowSeconds),
            model.texture,
            model.renderType,
            model.scale,
            model.shadowRadius,
            model.lightMode
        )
    }

    internal fun onAssetsReload() {
        loggedMissingGeometries.clear()
        BedrockGeoGpuMeshCache.requestInvalidation()
        BedrockGeoGpuRenderTypes.clear()
        BedrockGeoGpuRuntime.reset()
    }

    fun submit(
        data: BedrockGeoRenderData,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        packedLight: Int,
        packedOverlay: Int = OverlayTexture.NO_OVERLAY
    ) = submit(data, poseStack, collector, packedLight, packedOverlay, null, -1)

    fun submit(
        data: BedrockGeoRenderData,
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        packedLight: Int,
        packedOverlay: Int,
        renderTypeOverride: RenderType?,
        color: Int
    ) {
        poseStack.pushPose()
        poseStack.scale(data.scale, data.scale, data.scale)
        val modelLight = if (data.lightMode == GeoLightMode.FULL_BRIGHT) {
            LightCoordsUtil.FULL_BRIGHT
        } else {
            packedLight
        }
        val renderType = renderTypeOverride ?: renderType(data)
        if (
            renderTypeOverride == null && color == -1 && gpuRenderingEnabled &&
            !BedrockGeoGpuRuntime.failed &&
            collector is BedrockGeoGpuSubmitCollector &&
            data.renderType.supportsGpuInstancing &&
            BedrockGeoRenderCompatibility.canUseGpuRendering()
        ) {
            val pose = poseStack.last()
            collector.submitBedrockGeoGpu(
                BedrockGeoGpuSubmit(
                    data,
                    Matrix4f(pose.pose()),
                    Matrix3f(pose.normal()),
                    BedrockGeoGpuRenderTypes.get(data.texture, data.renderType),
                    modelLight,
                    packedOverlay
                )
            )
            poseStack.popPose()
            return
        }
        collector.submitCustomGeometry(poseStack, renderType) { basePose, consumer ->
            emitVertices(data, basePose, consumer, modelLight, packedOverlay, color)
        }
        poseStack.popPose()
    }

    fun emitVertices(data: BedrockGeoRenderData, basePose: PoseStack.Pose, consumer: VertexConsumer, modelLight: Int, packedOverlay: Int, color: Int) {
        val point = Vector3f()
        val normal = Vector3f()
        data.model.bones.forEachIndexed { boneIndex, bone ->
            val transform = data.pose.transforms[boneIndex]
            val normalTransform = data.pose.normalTransforms[boneIndex]
            bone.quads.forEach { quad ->
                normal.set(quad.normalX, quad.normalY, quad.normalZ)
                normalTransform.transform(normal).normalize()
                repeat(4) { vertexIndex ->
                    val positionIndex = vertexIndex * 3
                    transform.transformPosition(
                        quad.positions[positionIndex],
                        quad.positions[positionIndex + 1],
                        quad.positions[positionIndex + 2],
                        point
                    )
                    consumer.addVertex(basePose, point)
                        .setColor(color)
                        .setUv(quad.uvs[vertexIndex * 2], quad.uvs[vertexIndex * 2 + 1])
                        .setOverlay(packedOverlay)
                        .setLight(modelLight)
                        .setNormal(basePose, normal)
                }
            }
        }
    }

    @JvmStatic
    fun horizontalRotationDegrees(facing: Direction): Float = when (facing) {
        Direction.SOUTH -> 180F
        Direction.WEST -> 90F
        Direction.EAST -> -90F
        else -> 0F
    }

    private fun renderType(data: BedrockGeoRenderData): RenderType = when (data.renderType) {
        GeoRenderType.SOLID -> RenderTypes.entitySolid(data.texture)
        GeoRenderType.CUTOUT -> RenderTypes.entityCutoutCull(data.texture)
        GeoRenderType.TRANSLUCENT -> RenderTypes.entityTranslucent(data.texture, false)
        GeoRenderType.ADDITIVE -> RenderTypes.energySwirl(data.texture, 0F, 0F)
    }

    private val GeoRenderType.supportsGpuInstancing: Boolean
        get() = this == GeoRenderType.SOLID || this == GeoRenderType.CUTOUT
}
