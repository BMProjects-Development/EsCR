package com.algorithmlx.ecr.api.particle

import org.joml.Quaternionf
import org.joml.Quaternionfc
import org.joml.Vector3f
import org.joml.Vector3fc
import kotlin.math.sqrt

internal fun Float.lerp(other: Float, alpha: Float) = this + (other - this) * alpha

internal fun catmullRom(t: Float, a: Float, b: Float, c: Float, d: Float): Float {
    val v0 = -0.5f * a + 1.5f * b - 1.5f * c + 0.5f * d
    val v1 = a - 2.5f * b + 2f * c - 0.5f * d
    val v2 = -0.5f * a + 0.5f * c
    val tt = t * t
    return v0 * t * tt + v1 * tt + v2 * t + b
}

internal fun bezier(t: Float, a: Float, b: Float, c: Float, d: Float): Float {
    val ab = a.lerp(b, t)
    val bc = b.lerp(c, t)
    val cd = c.lerp(d, t)
    return ab.lerp(bc, t).lerp(bc.lerp(cd, t), t)
}

internal fun Vector3fc.rotated(rotation: Quaternionfc) = Vector3f(this).rotate(rotation)

internal fun Quaternionfc.opposite() = Quaternionf(this).mul(Y_ROTATION_180)

internal fun Quaternionfc.projectAroundAxis(axis: Vector3fc): Quaternionf {
    val projectedLength = axis.dot(x(), y(), z())
    val projected = Vector3f(axis).mul(projectedLength)
    return if (projectedLength > 0f) Quaternionf(projected.x, projected.y, projected.z, w()).normalize()
    else Quaternionf(-projected.x, -projected.y, -projected.z, -w()).normalize()
}

internal fun lookAt(direction: Vector3fc, up: Vector3fc): Quaternionf {
    if (direction.lengthSquared() < 1.0e-8f || up.lengthSquared() < 1.0e-8f) return Quaternionf()
    return Quaternionf().lookAlong(direction, up).normalize()
}

internal fun reflect(vector: Vector3fc, normal: Vector3fc) =
    Vector3f(normal).mul(-2f * vector.dot(normal)).add(vector)

internal fun packColor(color: ParticleColor): Int {
    fun channel(value: Float) = (value.coerceIn(0f, 1f) * 255f).toInt()
    return channel(color.a) shl 24 or
        (channel(color.r) shl 16) or
        (channel(color.g) shl 8) or
        channel(color.b)
}

internal fun safeNormalize(vector: Vector3f): Vector3f =
    if (vector.lengthSquared() > 1.0e-8f) vector.normalize() else vector.zero()

internal fun speed(vector: Vector3fc) = sqrt(vector.lengthSquared())

private val Y_ROTATION_180 = Quaternionf().rotationY(kotlin.math.PI.toFloat())
