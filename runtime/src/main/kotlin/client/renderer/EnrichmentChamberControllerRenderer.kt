package com.algorithmlx.ecr.client.renderer

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.common.block.entity.enrichment.EnrichmentChamberControllerEntity
import com.algorithmlx.ecr.mixin.client.RenderPipelinesAccessor
import com.algorithmlx.ecr.mixin.client.RenderTypeAccessor
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.feature.ModelFeatureRenderer
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import java.util.Random
import kotlin.math.floor

class EnrichmentChamberControllerRenderState: BlockEntityRenderState() {
    var innerBounds: AABB? = null
    var fill = 0F
    var fullyCharged = false
    var overflowing = false
    var animationTicks = 0.0
}

class EnrichmentChamberControllerRenderer(
    context: BlockEntityRendererProvider.Context
): BlockEntityRenderer<EnrichmentChamberControllerEntity, EnrichmentChamberControllerRenderState> {
    override fun createRenderState() = EnrichmentChamberControllerRenderState()

    override fun extractRenderState(
        blockEntity: EnrichmentChamberControllerEntity,
        state: EnrichmentChamberControllerRenderState,
        partialTicks: Float,
        cameraPosition: Vec3,
        breakProgress: ModelFeatureRenderer.CrumblingOverlay?
    ) {
        super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress)

        val storage = blockEntity.mruStorage
        val amount = storage.mru
        val capacity = storage.mruCapacity
        state.fill = when {
            amount <= 0 -> 0F
            capacity <= 0 -> 1F
            else -> (amount.toDouble() / capacity).coerceIn(0.0, 1.0).toFloat()
        }
        state.fullyCharged = capacity > 0 && amount >= capacity
        state.overflowing = amount > capacity
        state.animationTicks = (blockEntity.level?.gameTime ?: 0L) + partialTicks.toDouble()
        state.innerBounds = blockEntity.innerBounds?.move(
            -blockEntity.blockPos.x.toDouble(),
            -blockEntity.blockPos.y.toDouble(),
            -blockEntity.blockPos.z.toDouble()
        )
    }

    override fun submit(
        state: EnrichmentChamberControllerRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        camera: CameraRenderState
    ) {
        val bounds = state.innerBounds ?: return

        val centerX = (bounds.minX + bounds.maxX) * 0.5
        val centerY = (bounds.minY + bounds.maxY) * 0.5
        val centerZ = (bounds.minZ + bounds.maxZ) * 0.5
        val fullHalfX = ((bounds.maxX - bounds.minX - BOUNDS_INSET * 2.0) * 0.5)
            .coerceAtLeast(0.0)
        val fullHalfY = ((bounds.maxY - bounds.minY - BOUNDS_INSET * 2.0) * 0.5)
            .coerceAtLeast(0.0)
        val fullHalfZ = ((bounds.maxZ - bounds.minZ - BOUNDS_INSET * 2.0) * 0.5)
            .coerceAtLeast(0.0)
        if (fullHalfX == 0.0 || fullHalfY == 0.0 || fullHalfZ == 0.0) return

        poseStack.pushPose()
        poseStack.translate(centerX, centerY, centerZ)
        if (state.fill > 0F) {
            val halfX = fullHalfX * state.fill
            val halfY = fullHalfY * state.fill
            val halfZ = fullHalfZ * state.fill
            val lowColor = if (state.overflowing) OVERFLOW_LOW_COLOR else NORMAL_LOW_COLOR
            val highColor = if (state.overflowing) OVERFLOW_HIGH_COLOR else NORMAL_HIGH_COLOR

            submitNodeCollector.submitCustomGeometry(poseStack, ENERGY_RENDER_TYPE) { pose, consumer ->
                renderBox(
                    pose,
                    consumer,
                    -halfX.toFloat(),
                    -halfY.toFloat(),
                    -halfZ.toFloat(),
                    halfX.toFloat(),
                    halfY.toFloat(),
                    halfZ.toFloat(),
                    lowColor,
                    highColor
                )
                renderBox(
                    pose,
                    consumer,
                    (-halfX * MIDDLE_LAYER_SCALE).toFloat(),
                    (-halfY * MIDDLE_LAYER_SCALE).toFloat(),
                    (-halfZ * MIDDLE_LAYER_SCALE).toFloat(),
                    (halfX * MIDDLE_LAYER_SCALE).toFloat(),
                    (halfY * MIDDLE_LAYER_SCALE).toFloat(),
                    (halfZ * MIDDLE_LAYER_SCALE).toFloat(),
                    scaleAlpha(lowColor, MIDDLE_LAYER_ALPHA),
                    scaleAlpha(highColor, MIDDLE_LAYER_ALPHA)
                )
                renderBox(
                    pose,
                    consumer,
                    (-halfX * INNER_LAYER_SCALE).toFloat(),
                    (-halfY * INNER_LAYER_SCALE).toFloat(),
                    (-halfZ * INNER_LAYER_SCALE).toFloat(),
                    (halfX * INNER_LAYER_SCALE).toFloat(),
                    (halfY * INNER_LAYER_SCALE).toFloat(),
                    (halfZ * INNER_LAYER_SCALE).toFloat(),
                    scaleAlpha(lowColor, INNER_LAYER_ALPHA),
                    scaleAlpha(highColor, INNER_LAYER_ALPHA)
                )
            }
        }
        submitLightning(
            state,
            poseStack,
            submitNodeCollector,
            camera,
            centerX,
            centerY,
            centerZ,
            fullHalfX.toFloat(),
            fullHalfY.toFloat(),
            fullHalfZ.toFloat()
        )
        poseStack.popPose()
    }

    override fun shouldRenderOffScreen(): Boolean = true

    override fun shouldRender(blockEntity: EnrichmentChamberControllerEntity, cameraPosition: Vec3): Boolean {
        val bounds = blockEntity.innerBounds ?: return false
        val distance = getViewDistance().toDouble()
        return bounds.distanceToSqr(cameraPosition) <= distance * distance
    }

    override fun getViewDistance(): Int = Minecraft.getInstance().options.effectiveRenderDistance * 16

    private fun renderBox(
        pose: PoseStack.Pose,
        consumer: VertexConsumer,
        minX: Float,
        minY: Float,
        minZ: Float,
        maxX: Float,
        maxY: Float,
        maxZ: Float,
        lowColor: Int,
        highColor: Int
    ) {
        quad(pose, consumer, lowColor, lowColor, lowColor, lowColor,
            minX, minY, minZ, maxX, minY, minZ, maxX, minY, maxZ, minX, minY, maxZ)
        quad(pose, consumer, highColor, highColor, highColor, highColor,
            minX, maxY, maxZ, maxX, maxY, maxZ, maxX, maxY, minZ, minX, maxY, minZ)

        quad(pose, consumer, lowColor, lowColor, highColor, highColor,
            minX, minY, minZ, minX, minY, maxZ, minX, maxY, maxZ, minX, maxY, minZ)
        quad(pose, consumer, lowColor, lowColor, highColor, highColor,
            maxX, minY, maxZ, maxX, minY, minZ, maxX, maxY, minZ, maxX, maxY, maxZ)
        quad(pose, consumer, lowColor, lowColor, highColor, highColor,
            maxX, minY, minZ, minX, minY, minZ, minX, maxY, minZ, maxX, maxY, minZ)
        quad(pose, consumer, lowColor, lowColor, highColor, highColor,
            minX, minY, maxZ, maxX, minY, maxZ, maxX, maxY, maxZ, minX, maxY, maxZ)
    }

    private fun quad(
        pose: PoseStack.Pose,
        consumer: VertexConsumer,
        color1: Int,
        color2: Int,
        color3: Int,
        color4: Int,
        x1: Float,
        y1: Float,
        z1: Float,
        x2: Float,
        y2: Float,
        z2: Float,
        x3: Float,
        y3: Float,
        z3: Float,
        x4: Float,
        y4: Float,
        z4: Float
    ) {
        consumer.addVertex(pose, x1, y1, z1).setColor(color1)
        consumer.addVertex(pose, x2, y2, z2).setColor(color2)
        consumer.addVertex(pose, x3, y3, z3).setColor(color3)
        consumer.addVertex(pose, x4, y4, z4).setColor(color4)
    }

    private fun scaleAlpha(color: Int, scale: Double): Int {
        val alpha = (((color ushr 24) and 0xFF) * scale).toInt().coerceIn(0, 0xFF)
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun submitLightning(
        state: EnrichmentChamberControllerRenderState,
        poseStack: PoseStack,
        submitNodeCollector: SubmitNodeCollector,
        camera: CameraRenderState,
        centerX: Double,
        centerY: Double,
        centerZ: Double,
        halfX: Float,
        halfY: Float,
        halfZ: Float
    ) {
        val period = when {
            state.overflowing -> OVERFLOW_LIGHTNING_PERIOD
            state.fullyCharged -> FULL_LIGHTNING_PERIOD
            else -> NORMAL_LIGHTNING_PERIOD
        }
        val duration = when {
            state.overflowing -> OVERFLOW_LIGHTNING_DURATION
            state.fullyCharged -> FULL_LIGHTNING_DURATION
            else -> NORMAL_LIGHTNING_DURATION
        }
        val cycle = floor(state.animationTicks / period).toLong()
        val cycleRandom = Random(state.blockPos.asLong() xor (cycle * LIGHTNING_SEED_STEP))
        val actualDuration = duration * (
            MIN_DURATION_SCALE + cycleRandom.nextDouble() * (MAX_DURATION_SCALE - MIN_DURATION_SCALE)
        )
        val flashStart = cycleRandom.nextDouble() * (period - actualDuration).coerceAtLeast(0.0)
        val age = state.animationTicks - cycle * period - flashStart
        if (age !in 0.0..actualDuration) return

        val progress = age / actualDuration
        val growth = EnrichmentLightning.growth(progress)
        val lifetime = EnrichmentLightning.opacity(progress)
        val maxBoltCount = when {
            state.overflowing -> OVERFLOW_BOLT_COUNT
            state.fullyCharged -> FULL_BOLT_COUNT
            else -> NORMAL_BOLT_COUNT
        }
        val boltCount = selectBoltCount(cycleRandom, maxBoltCount)
        val cameraLocal = Vector3f(
            (camera.pos.x - (state.blockPos.x + centerX)).toFloat(),
            (camera.pos.y - (state.blockPos.y + centerY)).toFloat(),
            (camera.pos.z - (state.blockPos.z + centerZ)).toFloat()
        )
        val minimumSize = minOf(halfX, halfY, halfZ) * 2F
        val baseThickness = (minimumSize * 0.006F).coerceIn(0.008F, 0.035F)

        submitNodeCollector.submitCustomGeometry(poseStack, RenderTypes.lightning()) { pose, consumer ->
            repeat(boltCount) { boltIndex ->
                val seed = state.blockPos.asLong() xor (cycle * LIGHTNING_SEED_STEP) xor boltIndex.toLong()
                val boltRandom = Random(seed)
                val intensity = lifetime * (
                    MIN_LIGHTNING_INTENSITY +
                        boltRandom.nextDouble() * (MAX_LIGHTNING_INTENSITY - MIN_LIGHTNING_INTENSITY)
                    )
                val thickness = baseThickness * (
                    MIN_THICKNESS_SCALE +
                        boltRandom.nextFloat() * (MAX_THICKNESS_SCALE - MIN_THICKNESS_SCALE)
                    )
                val outerColor = scaleAlpha(
                    if (state.overflowing) OVERFLOW_LIGHTNING_OUTER_COLOR else NORMAL_LIGHTNING_OUTER_COLOR,
                    intensity
                )
                val coreColor = scaleAlpha(
                    if (state.overflowing) OVERFLOW_LIGHTNING_CORE_COLOR else NORMAL_LIGHTNING_CORE_COLOR,
                    intensity
                )
                val bolt = EnrichmentLightning.create(seed, halfX, halfY, halfZ, state.overflowing)
                renderLightning(
                    pose,
                    consumer,
                    bolt,
                    cameraLocal,
                    thickness,
                    outerColor,
                    coreColor,
                    growth,
                    state.animationTicks,
                    halfX,
                    halfY,
                    halfZ,
                    state.overflowing
                )
            }
        }
    }

    private fun selectBoltCount(random: Random, maximum: Int): Int {
        if (maximum <= 1) return 1

        val roll = random.nextFloat()
        return when {
            roll < SINGLE_BOLT_CHANCE -> 1
            maximum == 2 || roll < DOUBLE_BOLT_CHANCE -> 2
            else -> 3
        }
    }

    private fun renderLightning(
        pose: PoseStack.Pose,
        consumer: VertexConsumer,
        bolt: EnrichmentLightningBolt,
        cameraLocal: Vector3f,
        thickness: Float,
        outerColor: Int,
        coreColor: Int,
        growth: Float,
        animationTicks: Double,
        halfX: Float,
        halfY: Float,
        halfZ: Float,
        overflowing: Boolean
    ) {
        bolt.paths.forEach { path ->
            val points = EnrichmentLightning.samplePath(path, growth, animationTicks, halfX, halfY, halfZ, overflowing)
            if (points.size < 2) return@forEach
            val pathOuterColor = if (path.birth > 0F) scaleAlpha(outerColor, BRANCH_ALPHA) else outerColor
            val pathCoreColor = if (path.birth > 0F) scaleAlpha(coreColor, BRANCH_ALPHA) else coreColor
            val pathThickness = thickness * path.thicknessScale
            points.zipWithNext().forEach { (start, end) -> renderLightningLine(pose, consumer, start, end, cameraLocal, pathThickness, pathOuterColor, pathCoreColor) }
        }
    }

    private fun renderLightningLine(pose: PoseStack.Pose, consumer: VertexConsumer, start: Vector3f, end: Vector3f, cameraLocal: Vector3f, thickness: Float, outerColor: Int, coreColor: Int) {
        if (start.distanceSquared(end) < 0.000001F) return
        renderLightningSegment(pose, consumer, start, end, cameraLocal, thickness * 2.4F, outerColor)
        renderLightningSegment(pose, consumer, start, end, cameraLocal, thickness, coreColor)
    }

    private fun renderLightningSegment(
        pose: PoseStack.Pose,
        consumer: VertexConsumer,
        start: Vector3f,
        end: Vector3f,
        cameraLocal: Vector3f,
        thickness: Float,
        color: Int
    ) {
        val middle = Vector3f(start).add(end).mul(0.5F)
        val viewDirection = Vector3f(cameraLocal).sub(middle)
        val side = Vector3f(end).sub(start).cross(viewDirection)
        if (side.lengthSquared() < 0.000001F) side.set(0F, 1F, 0F)
        side.normalize(thickness)

        quad(
            pose,
            consumer,
            color,
            color,
            color,
            color,
            start.x - side.x,
            start.y - side.y,
            start.z - side.z,
            end.x - side.x,
            end.y - side.y,
            end.z - side.z,
            end.x + side.x,
            end.y + side.y,
            end.z + side.z,
            start.x + side.x,
            start.y + side.y,
            start.z + side.z
        )
        quad(
            pose,
            consumer,
            color,
            color,
            color,
            color,
            start.x + side.x,
            start.y + side.y,
            start.z + side.z,
            end.x + side.x,
            end.y + side.y,
            end.z + side.z,
            end.x - side.x,
            end.y - side.y,
            end.z - side.z,
            start.x - side.x,
            start.y - side.y,
            start.z - side.z
        )
    }

    companion object {
        private const val BOUNDS_INSET = 0.02
        private const val MIDDLE_LAYER_SCALE = 0.7
        private const val MIDDLE_LAYER_ALPHA = 0.48
        private const val INNER_LAYER_SCALE = 0.4
        private const val INNER_LAYER_ALPHA = 0.3
        private const val NORMAL_LIGHTNING_PERIOD = 140.0
        private const val NORMAL_LIGHTNING_DURATION = 24.0
        private const val FULL_LIGHTNING_PERIOD = 95.0
        private const val FULL_LIGHTNING_DURATION = 28.0
        private const val OVERFLOW_LIGHTNING_PERIOD = 65.0
        private const val OVERFLOW_LIGHTNING_DURATION = 34.0
        private const val NORMAL_BOLT_COUNT = 1
        private const val FULL_BOLT_COUNT = 2
        private const val OVERFLOW_BOLT_COUNT = 3
        private const val SINGLE_BOLT_CHANCE = 0.75F
        private const val DOUBLE_BOLT_CHANCE = 0.95F
        private const val MIN_DURATION_SCALE = 0.7
        private const val MAX_DURATION_SCALE = 1.25
        private const val MIN_LIGHTNING_INTENSITY = 0.35
        private const val MAX_LIGHTNING_INTENSITY = 1.0
        private const val MIN_THICKNESS_SCALE = 0.55F
        private const val MAX_THICKNESS_SCALE = 1.15F
        private const val BRANCH_ALPHA = 0.82
        private const val LIGHTNING_SEED_STEP = -7046029254386353131L

        private const val NORMAL_LOW_COLOR = 0x6050127A
        private const val NORMAL_HIGH_COLOR = 0x608B00FF
        private const val OVERFLOW_LOW_COLOR = 0x600A3696
        private const val OVERFLOW_HIGH_COLOR = 0x6000AEFF
        private const val NORMAL_LIGHTNING_OUTER_COLOR = 0x306620D9
        private const val NORMAL_LIGHTNING_CORE_COLOR = 0x70E2B0FF
        private const val OVERFLOW_LIGHTNING_OUTER_COLOR = 0x300078D9
        private const val OVERFLOW_LIGHTNING_CORE_COLOR = 0x70D8F8FF

        private val ENERGY_RENDER_TYPE: RenderType by lazy {
            val pipeline = RenderPipelinesAccessor.register(
                RenderPipeline.builder(RenderPipelinesAccessor.debugFilledSnippet())
                    .withLocation("pipeline/enrichment_chamber_energy".ecRL)
                    .withVertexShader("core/enrichment_chamber_energy".ecRL)
                    .withFragmentShader("core/enrichment_chamber_energy".ecRL)
                    .build()
            )

            RenderTypeAccessor.create(
                "${ModId}_enrichment_chamber_energy",
                RenderSetup.builder(pipeline)
                    .sortOnUpload()
                    .createRenderSetup()
            )
        }
    }
}
