package com.algorithmlx.ecr.client.renderer

import org.joml.Vector3f
import java.util.*
import kotlin.math.*

data class EnrichmentLightningPoint(val position: Vector3f, val phase: Float, val motionScale: Float)
data class EnrichmentLightningPath(val points: List<EnrichmentLightningPoint>, val birth: Float = 0F, val thicknessScale: Float = 1F)
data class EnrichmentLightningBolt(val paths: List<EnrichmentLightningPath>, val mainLength: Float)

object EnrichmentLightning {
    private const val BOUNDS_SCALE = 0.94F
    private const val SPAWN_SCALE = 0.5F
    private const val END_INSET = 0.96F
    private const val MINIMUM_LENGTH = 0.35F
    private const val MIN_LENGTH_SCALE = 0.12F
    private const val LENGTH_DISTRIBUTION = 0.72
    private const val SEGMENT_LENGTH = 0.32F
    private const val MIN_SEGMENTS = 3
    private const val MAX_SEGMENTS = 64
    private const val BRANCH_SEGMENTS = 7
    private const val MAX_BRANCHES = 3
    private const val MAX_OVERFLOW_BRANCHES = 4
    private const val BRANCH_FORWARD_WEIGHT = 0.35F
    private const val BRANCH_RANDOM_WEIGHT = 0.9F
    private const val MIN_BRANCH_LENGTH_SCALE = 0.15F
    private const val MAX_BRANCH_LENGTH_SCALE = 0.42F
    private const val MIN_BRANCH_LENGTH = 0.12F
    private const val BRANCH_SEGMENT_LENGTH = 0.28F
    private const val MIN_BRANCH_SEGMENTS = 2
    private const val MAX_BRANCH_SEGMENTS = 20
    private const val MIN_BRANCH_THICKNESS = 0.42F
    private const val MAX_BRANCH_THICKNESS = 0.72F
    private const val NORMAL_JITTER = 0.075F
    private const val OVERFLOW_JITTER = 0.12F
    private const val NORMAL_BRANCH_JITTER = 0.1F
    private const val OVERFLOW_BRANCH_JITTER = 0.16F
    private const val MIN_JITTER = 0.025F
    private const val MAX_JITTER = 0.7F
    private const val END_MOTION = 0.32F
    private const val MIN_MIDDLE_MOTION = 0.7F
    private const val MAX_MIDDLE_MOTION = 1.15F
    private const val GROWTH_END = 0.7
    private const val FADE_IN_END = 0.12
    private const val FADE_OUT_START = 0.3
    private const val MIN_FLICKER = 0.72
    private const val FLICKER_SPEED = 13.0
    private const val NORMAL_MOTION = 0.028F
    private const val OVERFLOW_MOTION = 0.045F
    private const val MIN_MOTION = 0.004F
    private const val MAX_MOTION = 0.18F
    private const val MAX_OVERFLOW_MOTION = 0.28F
    private const val MOTION_X_SPEED = 0.31F
    private const val MOTION_Y_SPEED = 0.23F
    private const val MOTION_Z_SPEED = 0.27F
    private const val MOTION_Y_PHASE = 1.37F
    private const val MOTION_Z_PHASE = 0.73F
    private const val MIN_DIRECTION_LENGTH = 0.000001F
    private const val MIN_DIRECTION_COMPONENT = 0.00001F
    private const val MIN_PROGRESS_RANGE = 0.0001F

    fun create(seed: Long, halfX: Float, halfY: Float, halfZ: Float, overflowing: Boolean): EnrichmentLightningBolt {
        val random = Random(seed)
        val limitX = halfX * BOUNDS_SCALE
        val limitY = halfY * BOUNDS_SCALE
        val limitZ = halfZ * BOUNDS_SCALE
        val start = Vector3f(randomCoordinate(random, limitX, SPAWN_SCALE), randomCoordinate(random, limitY, SPAWN_SCALE), randomCoordinate(random, limitZ, SPAWN_SCALE))
        val direction = randomDirection(random)
        val allowedLength = distanceToBounds(start, direction, limitX, limitY, limitZ) * END_INSET
        val maximumLength = minOf(maximumLength(halfX, halfY, halfZ), allowedLength).coerceAtLeast(MINIMUM_LENGTH)
        val minimumLength = maxOf(MINIMUM_LENGTH, maximumLength * MIN_LENGTH_SCALE).coerceAtMost(maximumLength)
        val lengthRoll = random.nextFloat().toDouble().pow(LENGTH_DISTRIBUTION).toFloat()
        val length = minimumLength + (maximumLength - minimumLength) * lengthRoll
        val segmentCount = ceil(length / SEGMENT_LENGTH).toInt().coerceIn(MIN_SEGMENTS, MAX_SEGMENTS)
        val mainPoints = createPoints(random, start, direction, length, segmentCount, if (overflowing) OVERFLOW_JITTER else NORMAL_JITTER, limitX, limitY, limitZ)
        val paths = mutableListOf(EnrichmentLightningPath(mainPoints))
        val branchLimit = (1 + segmentCount / BRANCH_SEGMENTS).coerceIn(1, if (overflowing) MAX_OVERFLOW_BRANCHES else MAX_BRANCHES)

        repeat(1 + random.nextInt(branchLimit)) {
            val rootIndex = 1 + random.nextInt((mainPoints.lastIndex - 1).coerceAtLeast(1))
            val root = mainPoints[rootIndex]
            val continuation = Vector3f(mainPoints[(rootIndex + 1).coerceAtMost(mainPoints.lastIndex)].position).sub(root.position)
            if (continuation.lengthSquared() < MIN_DIRECTION_LENGTH) continuation.set(0F, 1F, 0F) else continuation.normalize()
            val branchDirection = continuation.mul(BRANCH_FORWARD_WEIGHT).add(randomDirection(random).mul(BRANCH_RANDOM_WEIGHT))
            if (branchDirection.lengthSquared() < MIN_DIRECTION_LENGTH) branchDirection.set(0F, 1F, 0F) else branchDirection.normalize()
            val branchLimitLength = distanceToBounds(root.position, branchDirection, limitX, limitY, limitZ) * END_INSET
            val desiredLength = length * (MIN_BRANCH_LENGTH_SCALE + random.nextFloat() * (MAX_BRANCH_LENGTH_SCALE - MIN_BRANCH_LENGTH_SCALE))
            val branchLength = minOf(desiredLength, branchLimitLength)
            if (branchLength < MIN_BRANCH_LENGTH) return@repeat
            val branchSegments = ceil(branchLength / BRANCH_SEGMENT_LENGTH).toInt().coerceIn(MIN_BRANCH_SEGMENTS, MAX_BRANCH_SEGMENTS)
            val branchPoints = createPoints(random, root.position, branchDirection, branchLength, branchSegments, if (overflowing) OVERFLOW_BRANCH_JITTER else NORMAL_BRANCH_JITTER, limitX, limitY, limitZ).toMutableList()
            branchPoints[0] = root
            paths += EnrichmentLightningPath(branchPoints, rootIndex.toFloat() / mainPoints.lastIndex, MIN_BRANCH_THICKNESS + random.nextFloat() * (MAX_BRANCH_THICKNESS - MIN_BRANCH_THICKNESS))
        }

        return EnrichmentLightningBolt(paths, length)
    }

    fun growth(progress: Double): Float {
        val value = (progress / GROWTH_END).coerceIn(0.0, 1.0).toFloat()
        return value * value * (3F - 2F * value)
    }

    fun opacity(progress: Double): Double {
        val fadeIn = smooth((progress / FADE_IN_END).coerceIn(0.0, 1.0))
        val fadeOut = smooth(((1.0 - progress) / FADE_OUT_START).coerceIn(0.0, 1.0))
        return fadeIn * fadeOut * (MIN_FLICKER + (1.0 - MIN_FLICKER) * (sin(progress * PI * FLICKER_SPEED) * 0.5 + 0.5))
    }

    fun visibleProgress(path: EnrichmentLightningPath, growth: Float): Float = ((growth - path.birth) / (1F - path.birth).coerceAtLeast(MIN_PROGRESS_RANGE)).coerceIn(0F, 1F)

    fun visibleSegments(path: EnrichmentLightningPath, growth: Float): Float = path.points.lastIndex * visibleProgress(path, growth)

    fun samplePath(path: EnrichmentLightningPath, growth: Float, animationTicks: Double, halfX: Float, halfY: Float, halfZ: Float, overflowing: Boolean): List<Vector3f> {
        val visibleSegments = visibleSegments(path, growth)
        if (visibleSegments <= 0F || path.points.size < 2) return emptyList()
        val completeSegments = floor(visibleSegments).toInt().coerceAtMost(path.points.lastIndex)
        val points = ArrayList<Vector3f>(completeSegments + 2)
        for (index in 0..completeSegments) points += animated(path.points[index], animationTicks, halfX, halfY, halfZ, overflowing)
        if (completeSegments < path.points.lastIndex) {
            val partial = visibleSegments - completeSegments
            if (partial > 0F) points += Vector3f(points.last()).lerp(animated(path.points[completeSegments + 1], animationTicks, halfX, halfY, halfZ, overflowing), partial)
        }
        return points
    }

    fun animated(point: EnrichmentLightningPoint, animationTicks: Double, halfX: Float, halfY: Float, halfZ: Float, overflowing: Boolean): Vector3f {
        val baseAmplitude = minOf(halfX, halfY, halfZ) * if (overflowing) OVERFLOW_MOTION else NORMAL_MOTION
        val amplitude = (baseAmplitude * point.motionScale).coerceIn(MIN_MOTION, if (overflowing) MAX_OVERFLOW_MOTION else MAX_MOTION)
        val time = animationTicks.toFloat()
        val phase = point.phase
        return Vector3f((point.position.x + sin(time * MOTION_X_SPEED + phase) * amplitude).coerceIn(-halfX * BOUNDS_SCALE, halfX * BOUNDS_SCALE), (point.position.y + cos(time * MOTION_Y_SPEED + phase * MOTION_Y_PHASE) * amplitude).coerceIn(-halfY * BOUNDS_SCALE, halfY * BOUNDS_SCALE), (point.position.z + sin(time * MOTION_Z_SPEED + phase * MOTION_Z_PHASE) * amplitude).coerceIn(-halfZ * BOUNDS_SCALE, halfZ * BOUNDS_SCALE))
    }

    fun maximumLength(halfX: Float, halfY: Float, halfZ: Float): Float = maxOf(halfX, halfY, halfZ).coerceAtLeast(MINIMUM_LENGTH)

    private fun createPoints(random: Random, start: Vector3f, direction: Vector3f, length: Float, segmentCount: Int, jitterScale: Float, limitX: Float, limitY: Float, limitZ: Float): List<EnrichmentLightningPoint> {
        val jitter = (length * jitterScale).coerceIn(MIN_JITTER, MAX_JITTER)
        return List(segmentCount + 1) { index ->
            val progress = index.toFloat() / segmentCount
            val envelope = sin(PI * progress).toFloat()
            val position = Vector3f(start).fma(length * progress, direction).add(randomCoordinate(random, jitter) * envelope, randomCoordinate(random, jitter) * envelope, randomCoordinate(random, jitter) * envelope)
            position.x = position.x.coerceIn(-limitX, limitX)
            position.y = position.y.coerceIn(-limitY, limitY)
            position.z = position.z.coerceIn(-limitZ, limitZ)
            EnrichmentLightningPoint(position, random.nextFloat() * PI.toFloat() * 2F, END_MOTION + envelope * (MIN_MIDDLE_MOTION + random.nextFloat() * (MAX_MIDDLE_MOTION - MIN_MIDDLE_MOTION)))
        }
    }

    private fun randomDirection(random: Random): Vector3f {
        val direction = Vector3f(randomCoordinate(random, 1F), randomCoordinate(random, 1F), randomCoordinate(random, 1F))
        return if (direction.lengthSquared() < MIN_DIRECTION_LENGTH) direction.set(0F, 1F, 0F) else direction.normalize()
    }

    private fun distanceToBounds(start: Vector3f, direction: Vector3f, halfX: Float, halfY: Float, halfZ: Float): Float {
        var distance = Float.POSITIVE_INFINITY
        fun include(position: Float, delta: Float, halfExtent: Float) {
            if (abs(delta) < MIN_DIRECTION_COMPONENT) return
            distance = minOf(distance, ((if (delta > 0F) halfExtent else -halfExtent) - position) / delta)
        }
        include(start.x, direction.x, halfX)
        include(start.y, direction.y, halfY)
        include(start.z, direction.z, halfZ)
        return distance.coerceAtLeast(0F)
    }

    private fun randomCoordinate(random: Random, extent: Float, scale: Float = 1F): Float = (random.nextFloat() * 2F - 1F) * extent * scale

    private fun smooth(value: Double): Double = value * value * (3.0 - 2.0 * value)
}
