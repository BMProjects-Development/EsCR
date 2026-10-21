package com.algorithmlx.ecr.api.vfx

import org.joml.Matrix4f
import org.joml.Matrix3f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI

internal object VfxRenderFrame {
    fun prepare(node: VfxNodeRuntime, particle: VfxParticle?, partial: Float): Matrix4f {
        val instance = node.instance
        val context = instance.context
        val owner = node.particleOwner
        val matrix = if (particle == null || owner == null) {
            context.useEffect(instance.time, instance.effect.timeline.duration, instance.effect.timeline.loop)
            Matrix4f(node.matrix)
        } else {
            context.useParticle(particle, owner.emitter)
            val relative = Matrix4f(owner.matrix).invert().mul(node.matrix)
            val position = Vector3f(particle.previous).lerp(particle.position, partial)
            if (particle.local) Matrix4f(owner.matrix).translate(position).mul(relative)
            else Matrix4f().translate(position).rotate(particle.rotation).scale(particle.scale).mul(relative)
        }
        context.position = matrix.getTranslation(Vector3f())
        return Matrix4f().translation(instance.position.x.toFloat(), instance.position.y.toFloat(), instance.position.z.toFloat()).mul(matrix)
    }

    fun face(matrix: Matrix4f, node: VfxNodeRuntime, particle: VfxParticle?, camera: Vector3f, cameraRotation: Quaternionf) {
        val facing = node.spec.properties.string("facing", "CAMERA")
        val position = matrix.getTranslation(Vector3f())
        val scale = matrix.getScale(Vector3f())
        val normal = Vector3f(camera).sub(position)
        val rotation = when (facing) {
            "CAMERA" -> Quaternionf(cameraRotation).rotateY(PI.toFloat())
            "CAMERA_AXIS" -> {
                val axis = node.spec.properties.vector("facingAxis").takeIf { it.lengthSquared() > 0.000001f } ?: Vector3f(0f, 1f, 0f)
                axis.normalize()
                normal.fma(-normal.dot(axis), axis)
                if (normal.lengthSquared() <= 0.000001f) return
                orientation(normal.normalize().negate(), axis)
            }
            "VELOCITY" -> {
                val up = particle?.let { Vector3f(it.velocity) } ?: return
                if (particle.local) node.particleOwner?.matrix?.transformDirection(up)
                if (up.lengthSquared() <= 0.000001f || normal.lengthSquared() <= 0.000001f) return
                up.normalize()
                val right = Vector3f(up).cross(normal)
                if (right.lengthSquared() <= 0.000001f) return
                orientation(right.cross(up).normalize().negate(), up)
            }
            else -> return
        }
        matrix.translationRotateScale(position, rotation, scale)
        val spin = node.instance.timeline(node.spec, "transform.rotation", 2, node.spec.transform.rotation.z)
        matrix.rotateZ(Math.toRadians(spin.toDouble()).toFloat())
    }

    private fun orientation(normal: Vector3f, up: Vector3f): Quaternionf {
        val right = Vector3f(up).cross(normal).normalize()
        val vertical = Vector3f(normal).cross(right).normalize()
        return Quaternionf().setFromNormalized(Matrix3f().setColumn(0, right).setColumn(1, vertical).setColumn(2, normal))
    }
}
