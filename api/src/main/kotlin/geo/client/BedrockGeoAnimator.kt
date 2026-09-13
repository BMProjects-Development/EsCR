package com.algorithmlx.ecr.api.geo.client

import com.algorithmlx.ecr.api.geo.GeoAnimationPlayback
import com.algorithmlx.ecr.api.geo.GeoBlendMode
import com.algorithmlx.ecr.api.geo.GeoLoopMode
import com.algorithmlx.ecr.api.geo.file.BedrockAnimation
import com.algorithmlx.ecr.api.geo.file.BedrockAnimationChannel
import com.algorithmlx.ecr.api.geo.file.BedrockAnimationLoop
import com.algorithmlx.ecr.api.geo.file.GeoInterpolation
import com.algorithmlx.ecr.api.molang.compiler.eval
import com.algorithmlx.ecr.api.molang.runtime.MolangContext
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.abs

data class BedrockGeoPose(
    val transforms: Array<Matrix4f>,
    val normalTransforms: Array<Matrix3f>
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BedrockGeoPose

        if (!transforms.contentEquals(other.transforms)) return false
        if (!normalTransforms.contentEquals(other.normalTransforms)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = transforms.contentHashCode()
        result = 31 * result + normalTransforms.contentHashCode()
        return result
    }
}

object BedrockGeoAnimator {
    @JvmStatic
    fun pose(
        model: BakedGeoModel,
        playbacks: List<GeoAnimationPlayback>,
        context: MolangContext,
        nowSeconds: Double
    ): BedrockGeoPose {
        val states = Array(model.bones.size) { MutableBonePose() }
        playbacks.forEach { playback ->
            val animation = BedrockGeoAssets.animation(playback.animation) ?: return@forEach
            val time = animationTime(animation, playback, context, nowSeconds) ?: return@forEach
            val weight = (playback.weight * animation.blendWeight.eval(context)).coerceIn(0F, 1F)
            if (weight == 0F) return@forEach

            animation.bones.forEach { (boneName, channels) ->
                val boneIndex = model.boneIndices[boneName] ?: return@forEach
                val state = states[boneIndex]

                val blendMode = if (animation.overridePreviousAnimation) GeoBlendMode.OVERRIDE else playback.blend
                if (blendMode == GeoBlendMode.OVERRIDE) state.reset()

                channels.position?.let { channel ->
                    val value = sample(channel, time, context)
                    state.position.add(value.mul(weight))
                }
                channels.rotation?.let { channel ->
                    val value = sample(channel, time, context)
                    state.rotation.add(value.mul(weight))
                }
                channels.scale?.let { channel ->
                    val value = sample(channel, time, context)
                    blendScale(state.scale, value, weight, blendMode)
                }
            }
        }

        val transforms = Array(model.bones.size) { Matrix4f() }
        val normals = Array(model.bones.size) { Matrix3f() }
        model.bones.forEachIndexed { index, bone ->
            val state = states[index]
            val matrix = if (bone.parentIndex >= 0) Matrix4f(transforms[bone.parentIndex]) else Matrix4f()
            matrix.translate(-state.position.x / MODEL_UNITS, state.position.y / MODEL_UNITS, state.position.z / MODEL_UNITS)
                .translate(bone.pivotX, bone.pivotY, bone.pivotZ)
                .rotateZ(bone.rotationZ + radians(state.rotation.z))
                .rotateY(bone.rotationY + radians(-state.rotation.y))
                .rotateX(bone.rotationX + radians(-state.rotation.x))
                .scale(state.scale)
                .translate(-bone.pivotX, -bone.pivotY, -bone.pivotZ)
            transforms[index] = matrix
            normals[index] = (if (bone.parentIndex >= 0) Matrix3f(normals[bone.parentIndex]) else Matrix3f())
                .rotateZ(bone.rotationZ + radians(state.rotation.z))
                .rotateY(bone.rotationY + radians(-state.rotation.y))
                .rotateX(bone.rotationX + radians(-state.rotation.x))
                .scale(
                    safeNormalScale(state.scale.x),
                    safeNormalScale(state.scale.y),
                    safeNormalScale(state.scale.z)
                )
        }

        return BedrockGeoPose(transforms, normals)
    }

    internal fun animationTime(
        animation: BedrockAnimation,
        playback: GeoAnimationPlayback,
        context: MolangContext,
        nowSeconds: Double
    ): Float? {
        val elapsed = ((nowSeconds - playback.startTimeSeconds) * abs(playback.speed)).toFloat() - animation.startDelay.eval(context)
        if (elapsed < 0F) return null
        val length = animation.lengthSeconds
        if (length <= 0F) return 0F

        val forwardTime = when (when (playback.loop) {
            GeoLoopMode.FROM_FILE -> animation.loop
            GeoLoopMode.ONCE -> BedrockAnimationLoop.ONCE
            GeoLoopMode.LOOP -> BedrockAnimationLoop.LOOP
            GeoLoopMode.HOLD -> BedrockAnimationLoop.HOLD
        }) {
            BedrockAnimationLoop.ONCE -> elapsed.takeIf { it <= length }
            BedrockAnimationLoop.HOLD -> elapsed.coerceAtMost(length)
            BedrockAnimationLoop.LOOP -> {
                val delay = animation.loopDelay.eval(context).coerceAtLeast(0F)
                val cycle = length + delay
                val cycleTime = if (cycle == 0F) 0F else elapsed.mod(cycle)
                cycleTime.coerceAtMost(length)
            }
        }
        return forwardTime?.let { time -> if (playback.speed < 0F) length - time else time }
    }

    internal fun isFinished(
        animation: BedrockAnimation,
        playback: GeoAnimationPlayback,
        context: MolangContext,
        nowSeconds: Double
    ): Boolean {
        val loop = when (playback.loop) {
            GeoLoopMode.FROM_FILE -> animation.loop
            GeoLoopMode.ONCE -> BedrockAnimationLoop.ONCE
            GeoLoopMode.LOOP -> BedrockAnimationLoop.LOOP
            GeoLoopMode.HOLD -> BedrockAnimationLoop.HOLD
        }
        if (loop != BedrockAnimationLoop.ONCE) return false

        val elapsed = ((nowSeconds - playback.startTimeSeconds) * abs(playback.speed)).toFloat() - animation.startDelay.eval(context)
        return elapsed > animation.lengthSeconds.coerceAtLeast(0F)
    }

    private fun sample(channel: BedrockAnimationChannel, time: Float, context: MolangContext): Vector3f {
        val frames = channel.keyframes
        if (frames.isEmpty()) return Vector3f()
        if (frames.size == 1 || time <= frames.first().timeSeconds) return frames.first().post.eval(context)
        if (time >= frames.last().timeSeconds) return frames.last().post.eval(context)

        val upperIndex = frames.indexOfFirst { it.timeSeconds >= time }.coerceAtLeast(1)
        val lowerIndex = upperIndex - 1
        val lower = frames[lowerIndex]
        val upper = frames[upperIndex]
        val progress = ((time - lower.timeSeconds) / (upper.timeSeconds - lower.timeSeconds)).coerceIn(0F, 1F)
        val start = lower.post.eval(context)
        val end = upper.pre.eval(context)

        return if (upper.interpolation == GeoInterpolation.CATMULL_ROM) {
            val before = frames.getOrNull(lowerIndex - 1)?.post?.eval(context) ?: Vector3f(start)
            val after = frames.getOrNull(upperIndex + 1)?.pre?.eval(context) ?: Vector3f(end)
            catmullRom(before, start, end, after, progress)
        } else {
            start.lerp(end, progress)
        }
    }

    private fun catmullRom(p0: Vector3f, p1: Vector3f, p2: Vector3f, p3: Vector3f, t: Float): Vector3f {
        val t2 = t * t
        val t3 = t2 * t
        fun component(a: Float, b: Float, c: Float, d: Float): Float =
            0.5F * ((2F * b) + (-a + c) * t + (2F * a - 5F * b + 4F * c - d) * t2 + (-a + 3F * b - 3F * c + d) * t3)
        return Vector3f(
            component(p0.x, p1.x, p2.x, p3.x),
            component(p0.y, p1.y, p2.y, p3.y),
            component(p0.z, p1.z, p2.z, p3.z)
        )
    }

    private fun radians(degrees: Float): Float = degrees * (PI.toFloat() / 180F)

    internal fun blendScale(
        current: Vector3f,
        sampled: Vector3f,
        weight: Float,
        blendMode: GeoBlendMode
    ) {
        if (blendMode == GeoBlendMode.OVERRIDE) {
            current.lerp(sampled, weight)
        } else {
            current.add(
                (sampled.x - 1F) * weight,
                (sampled.y - 1F) * weight,
                (sampled.z - 1F) * weight
            )
        }
    }

    internal fun safeNormalScale(scale: Float): Float =
        if (scale.isFinite() && abs(scale) > MIN_NORMAL_SCALE) 1F / scale else 1F

    private class MutableBonePose {
        val position = Vector3f()
        val rotation = Vector3f()
        val scale = Vector3f(1F, 1F, 1F)

        fun reset() {
            position.zero()
            rotation.zero()
            scale.set(1F)
        }
    }

    private const val MODEL_UNITS = 16F
    private const val MIN_NORMAL_SCALE = 1E-6F
}
