package com.algorithmlx.ecr.client.renderer

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.molang.runtime.Math.abs
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.mixin.client.RenderPipelinesAccessor
import com.algorithmlx.ecr.mixin.client.RenderTypeAccessor
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.client.renderer.state.level.LevelRenderState
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.*

object MRULinkRenderer {
    private const val CROSS_DIMENSION_HEIGHT = 5.0

    private const val BALLS_PER_BLOCK = 3F

    private const val MIN_BALL_COUNT = 8
    private const val MAX_BALL_COUNT = 56

    private const val BALL_SIZE = 0.085F

    private const val RADIUS = 0.2F

    private const val TURNS = 0.35F

    private const val FLOW_SPEED_BLOCKS_PER_SECOND = 2.4
    private const val FADE_OUT_DURATION_SECONDS = 0.6
    private const val ALPHA_FADE_DISTANCE = 0.4F
    private const val RADIUS_FADE_DISTANCE = 0.7F
    private const val MIN_PATH_LENGTH = 0.01F

    private const val TAU = (PI * 2.0).toFloat()

    private var renderedLevel: ClientLevel? = null
    private var viewerWasActive = false
    private val activeConnections = mutableMapOf<ConnectionKey, ConnectionAnimation>()
    private var lastActiveBalls: List<BallData> = emptyList()
    private var fadingTrail: FadingTrail? = null

    private val renderType: RenderType by lazy {
        val pipeline = RenderPipelinesAccessor.register(
            RenderPipeline.builder(RenderPipelinesAccessor.debugFilledSnippet())
                .withLocation("pipeline/helix".ecRL)
                .withVertexShader("core/helix".ecRL)
                .withFragmentShader("core/helix".ecRL)
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .build()
        )

        RenderTypeAccessor.create("${ModId}_helix", RenderSetup.builder(pipeline)
            .sortOnUpload()
            .createRenderSetup()
        )
    }

    @JvmStatic
    fun clear() {
        renderedLevel = null
        resetAnimation()
    }

    fun submit(poseStack: PoseStack, collector: SubmitNodeCollector, state: LevelRenderState) {
        val mc = Minecraft.getInstance()
        val level = mc.level ?: run {
            clear()
            return
        }
        val player = mc.player ?: run {
            clear()
            return
        }

        if (renderedLevel !== level) {
            resetAnimation()
            renderedLevel = level
        }

        val camera = state.cameraRenderState
        val cameraPos = camera.pos
        val renderDistance = mc.options.effectiveRenderDistance * 16.0
        val renderDistanceSquared = renderDistance * renderDistance
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(false)
        val timeSeconds = (level.gameTime.toDouble() + partialTick.toDouble()) / 20.0
        val viewerActive = MRULinkViewerAccess.isActive(player)

        val batch = if (viewerActive) {
            if (!viewerWasActive) {
                activeConnections.clear()
                fadingTrail = null
            }

            val balls = collectActiveBalls(level, timeSeconds, camera, renderDistanceSquared)
            lastActiveBalls = balls
            RenderBatch(balls, 1F)
        } else {
            if (viewerWasActive) {
                fadingTrail = lastActiveBalls.takeIf(List<*>::isNotEmpty)?.let { FadingTrail(it, timeSeconds) }
                activeConnections.clear()
                lastActiveBalls = emptyList()
            }

            fadingBatch(timeSeconds)
        }

        viewerWasActive = viewerActive

        if (batch.balls.isEmpty() || batch.opacity <= 0.001F) return

        val billboardRotation = Quaternionf(camera.orientation).mul(Quaternionf().rotationY(PI.toFloat()))

        collector.submitCustomGeometry(poseStack, renderType) { pose, consumer ->
            batch.balls.forEach { ball ->
                renderBall(pose, consumer, ball, billboardRotation, cameraPos, batch.opacity)
            }
        }
    }

    private fun collectActiveBalls(
        level: ClientLevel,
        timeSeconds: Double,
        camera: CameraRenderState,
        renderDistanceSquared: Double
    ): List<BallData> {
        val balls = ArrayList<BallData>()
        val seenConnections = HashSet<ConnectionKey>()

        MRULinkClientTracker.snapshot(level).forEach {
            val device = it as? MRUDevice ?: return@forEach
            val locator = device.locator ?: return@forEach
            val devicePos = (locator.position ?: it.blockPos).immutable()
            val deviceCenter = Vec3.atCenterOf(devicePos)
            val locatorStack = locator.locatorStorage.getItem(locator.locatorSlot)
            val locatorItem = locatorStack.item as? BoundGem ?: return@forEach
            val boundPos = locatorItem.getBoundPos(locatorStack) ?: return@forEach
            val boundDimension = locatorItem.getWorld(locatorStack)

            val start: Vec3
            val end: Vec3

            if (boundDimension == null || boundDimension == level.dimension()) {
                if (!locatorItem.isWithinBoundRadius(devicePos, boundPos)) return@forEach

                start = Vec3.atCenterOf(boundPos)
                end = deviceCenter
            } else {
                start = deviceCenter
                end = deviceCenter.add(0.0, CROSS_DIMENSION_HEIGHT, 0.0)
            }

            val key = ConnectionKey(it.blockPos.immutable(), start, end)
            val animation = activeConnections.getOrPut(key) { ConnectionAnimation(timeSeconds) }
            seenConnections += key

            if (camera.pos.distanceToSqr(deviceCenter) > renderDistanceSquared) return@forEach

            appendConnectionBalls(
                balls,
                start,
                end,
                (timeSeconds - animation.startedAt).coerceAtLeast(0.0),
                camera,
                renderDistanceSquared
            )
        }

        activeConnections.keys.retainAll(seenConnections)
        return balls
    }

    private fun fadingBatch(timeSeconds: Double): RenderBatch {
        val trail = fadingTrail ?: return RenderBatch(emptyList(), 0F)
        val progress = ((timeSeconds - trail.startedAt) / FADE_OUT_DURATION_SECONDS).toFloat().coerceIn(0F, 1F)

        if (progress >= 1F) {
            fadingTrail = null
            return RenderBatch(emptyList(), 0F)
        }

        val opacity = 1F - smoothstep(0F, 1F, progress)
        return RenderBatch(trail.balls, opacity)
    }

    private fun resetAnimation() {
        viewerWasActive = false
        activeConnections.clear()
        lastActiveBalls = emptyList()
        fadingTrail = null
    }

    private fun appendConnectionBalls(
        output: MutableList<BallData>,
        start: Vec3,
        end: Vec3,
        elapsedSeconds: Double,
        camera: CameraRenderState,
        renderDistanceSquared: Double
    ) {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z

        val pathLength = sqrt(dx * dx + dy * dy + dz * dz)

        if (pathLength < MIN_PATH_LENGTH) return

        val direction = Vector3f((dx / pathLength).toFloat(), (dy / pathLength).toFloat(), (dz / pathLength).toFloat())

        val reference = if (abs(direction.y) < 0.9F) Vector3f(0F, 1F, 0F) else Vector3f(1F, 0F, 0F)

        val side = Vector3f(direction).cross(reference).normalize()
        val up = Vector3f(direction).cross(side).normalize()

        val ballCount = (pathLength.toFloat() * BALLS_PER_BLOCK).roundToInt().coerceIn(MIN_BALL_COUNT, MAX_BALL_COUNT)
        val headProgress = elapsedSeconds * FLOW_SPEED_BLOCKS_PER_SECOND / pathLength

        (0 ..< ballCount).forEach { index ->
            val launchOffset = index.toDouble() / ballCount.toDouble()
            val traveled = headProgress - launchOffset

            if (traveled < 0.0) return@forEach

            val t = (traveled % 1.0).toFloat()

            val radiusEnvelope = endpointEnvelope(t, pathLength.toFloat(), RADIUS_FADE_DISTANCE)
            val radius = RADIUS * radiusEnvelope

            val angle = (t * pathLength.toFloat() * TURNS * TAU).toDouble()

            val cosAngle = cos(angle).toFloat()
            val sinAngle = sin(angle).toFloat()

            val centerX = start.x + dx * t
            val centerY = start.y + dy * t
            val centerZ = start.z + dz * t

            val spiralX = (side.x * cosAngle + up.x * sinAngle) * radius
            val spiralY = (side.y * cosAngle + up.y * sinAngle) * radius
            val spiralZ = (side.z * cosAngle + up.z * sinAngle) * radius

            val worldX = centerX + spiralX
            val worldY = centerY + spiralY
            val worldZ = centerZ + spiralZ

            val cameraDx = worldX - camera.pos.x
            val cameraDy = worldY - camera.pos.y
            val cameraDz = worldZ - camera.pos.z

            val cameraDistanceSquared = cameraDx * cameraDx + cameraDy * cameraDy + cameraDz * cameraDz

            if (cameraDistanceSquared > renderDistanceSquared) return@forEach

            val alpha = endpointEnvelope(t, pathLength.toFloat(), ALPHA_FADE_DISTANCE)

            if (alpha <= 0.001F) return@forEach

            val position = Vec3(worldX, worldY, worldZ)
            val phase = index.toFloat() / ballCount.toFloat()

            output += BallData(position, alpha, phase)
        }
    }

    private fun renderBall(
        pose: PoseStack.Pose,
        consumer: VertexConsumer,
        ball: BallData,
        rotation: Quaternionf,
        cameraPosition: Vec3,
        opacity: Float
    ) {
        val encodedPhase = (ball.phase * 255F).toInt().coerceIn(0, 255)
        val encodedAlpha = (ball.alpha * opacity * 255F).toInt().coerceIn(0, 255)
        val cameraRelativePosition = Vector3f(
            (ball.position.x - cameraPosition.x).toFloat(),
            (ball.position.y - cameraPosition.y).toFloat(),
            (ball.position.z - cameraPosition.z).toFloat()
        )

        fun vertex(x: Float, y: Float, u: Float, v: Float) {
            val point = Vector3f(x, y, 0F).rotate(rotation).add(cameraRelativePosition)

            consumer.addVertex(pose, point.x, point.y, point.z)
                .setColor(if (u > 0.5F) 255 else 0, if (v > 0.5F) 255 else 0, encodedPhase, encodedAlpha)
        }

        vertex(-BALL_SIZE, -BALL_SIZE, 0F, 1F)
        vertex(-BALL_SIZE, BALL_SIZE, 0F, 0F)
        vertex(BALL_SIZE, BALL_SIZE, 1F, 0F)
        vertex(BALL_SIZE, -BALL_SIZE, 1F, 1F)
    }

    private fun endpointEnvelope(t: Float, pathLength: Float, worldFadeDistance: Float): Float {
        val edge = (worldFadeDistance / pathLength).coerceIn(0.015F, 0.28F)
        val start = smoothstep(0F, edge, t)
        val end = 1F - smoothstep(1F - edge, 1F, t)

        return start * end
    }

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge0 == edge1) return if (value < edge0) 0F else 1F

        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0F, 1F)
        return  t * t * (3F - 2F * t)
    }

    private data class ConnectionKey(val devicePosition: BlockPos, val start: Vec3, val end: Vec3)
    private data class ConnectionAnimation(val startedAt: Double)
    private data class BallData(val position: Vec3, val alpha: Float, val phase: Float)
    private data class FadingTrail(val balls: List<BallData>, val startedAt: Double)
    private data class RenderBatch(val balls: List<BallData>, val opacity: Float)
}
