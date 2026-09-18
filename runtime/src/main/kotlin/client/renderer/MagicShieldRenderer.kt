package com.algorithmlx.ecr.client.renderer

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.mixin.client.RenderPipelinesAccessor
import com.algorithmlx.ecr.mixin.client.RenderTypeAccessor
import com.algorithmlx.ecr.network.MagicShieldPayload
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.state.level.LevelRenderState
import net.minecraft.util.Mth
import org.joml.Vector3f
import kotlin.math.abs

object MagicShieldRenderer {
    private val waves = mutableListOf<MagicShieldWave>()
    private val magicShieldRenderType: RenderType by lazy {
        val pipeline = RenderPipelinesAccessor.register(
            RenderPipeline.builder(RenderPipelinesAccessor.debugFilledSnippet())
                .withLocation("pipeline/magic_shield".ecRL)
                .withVertexShader("core/magic_shield".ecRL)
                .withFragmentShader("core/magic_shield".ecRL)
                .withCull(false)
                .build()
        )

        RenderTypeAccessor.create(
            "${ModId}_magic_shield",
            RenderSetup.builder(pipeline).sortOnUpload().createRenderSetup()
        )
    }

    @JvmStatic
    fun accept(payload: MagicShieldPayload) {
        val level = Minecraft.getInstance().level ?: return
        val hitPosition = Vector3f(payload.hitX, payload.hitY, payload.hitZ)
        val maxAxis = maxOf(abs(hitPosition.x), abs(hitPosition.y), abs(hitPosition.z))

        if (hitPosition.lengthSquared() < MIN_DIRECTION_LENGTH) hitPosition.set(0.0F, 0.0F, -1.0F)
        else hitPosition.div(maxAxis)

        waves += MagicShieldWave(
            payload.entityId, hitPosition, payload.blocked, level.gameTime.toDouble()
        )
    }

    @JvmStatic
    fun clear() {
        waves.clear()
    }

    @JvmStatic
    fun submit(poseStack: PoseStack, collector: SubmitNodeCollector, levelRenderState: LevelRenderState) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val partialTick = minecraft.deltaTracker.getGameTimeDeltaPartialTick(false)
        val currentTime = level.gameTime + partialTick.toDouble()
        val camera = levelRenderState.cameraRenderState.pos
        val renderDistance = minecraft.options.effectiveRenderDistance * 16.0
        val renderDistanceSquare = renderDistance * renderDistance

        val iterator = waves.iterator()
        while (iterator.hasNext()) {
            val wave = iterator.next()
            val age = currentTime - wave.startedAt
            val entity = level.getEntity(wave.entityId)

            if (age >= DURATION_TICKS || entity == null || entity.isRemoved) {
                iterator.remove()
                continue
            }

            val progress = (age / DURATION_TICKS).toFloat().coerceIn(0.0F, 1f)

            val x = Mth.lerp(partialTick.toDouble(), entity.xo, entity.x)
            val y = Mth.lerp(partialTick.toDouble(), entity.yo, entity.y)
            val z = Mth.lerp(partialTick.toDouble(), entity.zo, entity.z)

            if (camera.distanceToSqr(x, y + entity.bbHeight * 0.5F, z) > renderDistanceSquare) continue

            val radiusX = (entity.bbWidth * 0.5F + SHIELD_MARGIN).coerceAtLeast(MIN_RADIUS)
            val radiusY = (entity.bbHeight * 0.5F + SHIELD_MARGIN).coerceAtLeast(MIN_RADIUS)

            poseStack.pushPose()
            poseStack.translate(
                x - camera.x,
                y + entity.bbHeight * 0.5F - camera.y,
                z - camera.z
            )

            collector.submitCustomGeometry(poseStack, magicShieldRenderType) { pose, cons ->
                renderShield(pose, cons, radiusX, radiusY, radiusX, wave.hitPosition, progress, wave.blocked)
            }

            poseStack.popPose()
        }
    }

    private fun renderShield(
        pose: PoseStack.Pose,
        consumet: VertexConsumer,
        radiusX: Float, radiusY: Float, radiusZ: Float,
        hitPosition: Vector3f,
        progress: Float,
        blocked: Boolean
    ) {
        val hit = Vector3f(hitPosition).mul(radiusX, radiusY, radiusZ)
        val maxDistance = Vector3f(radiusX + abs(hit.x), radiusY + abs(hit.y), radiusZ + abs(hit.z)).length()

        for (face in 0 ..< 6) {
            for (row in 0 ..< FACE_SEGMENTS) {
                val startV = -1F + 2F * row / FACE_SEGMENTS
                val endV = -1F + 2F * (row + 1) / FACE_SEGMENTS

                for (column in 0 ..< FACE_SEGMENTS) {
                    val startU = -1F + 2F * column / FACE_SEGMENTS
                    val endU = -1F + 2F * (column + 1) / FACE_SEGMENTS

                    val first = position(face, startU, startV, radiusX, radiusY, radiusZ)
                    val second = position(face, startU, endV, radiusX, radiusY, radiusZ)
                    val third = position(face, endU, endV, radiusX, radiusY, radiusZ)
                    val fourth = position(face, endU, startV, radiusX, radiusY, radiusZ)

                    vertex(pose, consumet, first, hit, maxDistance, progress, blocked)
                    vertex(pose, consumet, second, hit, maxDistance, progress, blocked)
                    vertex(pose, consumet, third, hit, maxDistance, progress, blocked)
                    vertex(pose, consumet, fourth, hit, maxDistance, progress, blocked)
                }
            }
        }
    }

    private fun vertex(
        pose: PoseStack.Pose,
        consumer: VertexConsumer,
        position: Vector3f, hitPosition: Vector3f,
        maxDistance: Float,
        progress: Float,
        blocked: Boolean
    ) {
        val distance = position.distance(hitPosition) / maxDistance
        val encodedDistance = (distance * 255F).toInt().coerceIn(0, 255)
        val encodedProgress = (progress * 255F).toInt().coerceIn(0, 255)

        val strength = if (blocked) 255 else 190

        consumer.addVertex(pose, position.x, position.y, position.z)
            .setColor(encodedDistance, encodedProgress, strength, 255)
    }

    private fun position(face: Int, u: Float, v: Float, radiusX: Float, radiusY: Float, radiusZ: Float): Vector3f {
        return when (face) {
            0 -> Vector3f(-radiusX, v * radiusY, u * radiusZ)
            1 -> Vector3f(radiusX, v * radiusY, u * radiusZ)
            2 -> Vector3f(u * radiusX, -radiusY, v * radiusZ)
            3 -> Vector3f(u * radiusX, radiusY, v * radiusZ)
            4 -> Vector3f(u * radiusX, v * radiusY, -radiusZ)
            else -> Vector3f(u * radiusX, v * radiusY, radiusZ)
        }
    }

    data class MagicShieldWave(
        val entityId: Int,
        val hitPosition: Vector3f,
        val blocked: Boolean,
        val startedAt: Double
    )

    private const val FACE_SEGMENTS = 16
    private const val DURATION_TICKS = 18.0
    private const val SHIELD_MARGIN = 0.08F
    private const val MIN_RADIUS = 0.15F
    private const val MIN_DIRECTION_LENGTH = 0.000001F
}
