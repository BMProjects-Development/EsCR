package com.algorithmlx.ecr.api.vfx

import kotlinx.serialization.json.JsonObject
import org.joml.Matrix4f
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal class VfxRibbonRenderer(
    private val camera: Vector3f,
    private val config: (VfxNodeRuntime) -> VfxSurfaceConfig,
    private val emit: (VfxNodeRuntime, JsonObject, Vector3f, List<VfxVertex>) -> Unit
) {
    private data class Sample(val width: Float, val color: Int)

    fun beam(node: VfxNodeRuntime, particle: VfxParticle?, partial: Float) {
        val instance = node.instance
        val context = instance.context
        val spec = node.spec.properties
        val surface = config(node)
        val frame = VfxRenderFrame.prepare(node, particle, partial)
        val start = frame.getTranslation(Vector3f())
        val target = instance.node(spec.string("target"))
        val endpoint = surface.end
        val end = if (target == null)
            frame.transformPosition(node.sample("beam.end", endpoint))
        else
            target.matrix.transformPosition(Vector3f()).add(instance.position.x.toFloat(), instance.position.y.toFloat(), instance.position.z.toFloat())
        context.cameraDistance = start.distance(camera)
        val particleColor = node.sample("appearance.color", surface.color)
        val segments = spec.number("segments", 24f).toInt().coerceIn(1, 128)
        val strength = node.sample("beam.noise", surface.noise).coerceAtLeast(0f)
        val length = start.distance(end)
        var previous = start
        var previousSample = sample(node, 0f, particleColor)
        var distance = 0f
        for (index in 1..segments) {
            val t = index.toFloat() / segments
            val next = Vector3f(start).lerp(end, t)
            if (index < segments && strength > 0f) {
                val wave = t * spec.number("noiseFrequency", 1.5f) * length + instance.time * spec.number("noiseSpeed", 4f)
                val amplitude = sin(t * PI.toFloat()) * strength
                next.add(sin(wave * 2f * PI.toFloat()) * amplitude, cos(wave * 1.5f * PI.toFloat()) * amplitude, sin(wave * PI.toFloat()) * amplitude)
            }
            val nextSample = sample(node, t, particleColor)
            val nextDistance = distance + previous.distance(next)
            ribbon(node, previous, next, previousSample, nextSample, uv(node, (index - 1f) / segments, distance), uv(node, t, nextDistance))
            previous = next
            previousSample = nextSample
            distance = nextDistance
        }
        context.progressOverride = null
    }

    fun trail(node: VfxNodeRuntime, particle: VfxParticle?) {
        val instance = node.instance
        val context = instance.context
        val spec = node.spec.properties
        val lifetime = spec.number("lifetime", 0.5f).coerceAtLeast(0.001f)
        val age = particle?.age ?: instance.time
        val points = (particle?.trail ?: node.trail).filter { age - it.second <= lifetime }
            .take(spec.number("maxPoints", 32f).toInt().coerceIn(2, 128))
        if (points.size < 2) return
        if (particle == null)
            context.useEffect(instance.time, instance.effect.timeline.duration, instance.effect.timeline.loop)
        else
            context.useParticle(particle, node.particleOwner?.emitter)
        val particleColor = node.sample("appearance.color", config(node).color)
        val offset = Vector3f(instance.position.x.toFloat(), instance.position.y.toFloat(), instance.position.z.toFloat())
        if (particle != null) {
            val owner = node.particleOwner ?: return
            val relative = Matrix4f(owner.matrix).invert().mul(node.matrix).getTranslation(Vector3f())
            if (particle.local) owner.matrix.transformDirection(relative)
            else relative.mul(particle.scale).rotate(particle.rotation)
            offset.add(relative)
        }
        var distance = 0f
        points.zipWithNext().forEachIndexed { index, (a, b) ->
            val start = Vector3f(a.first)
            val end = Vector3f(b.first)
            if (particle?.local == true) {
                node.particleOwner?.matrix?.transformPosition(start)
                node.particleOwner?.matrix?.transformPosition(end)
            }
            start.add(offset)
            end.add(offset)
            context.cameraDistance = start.distance(camera)
            val from = sample(node, ((age - a.second) / lifetime).coerceIn(0f, 1f), particleColor)
            val to = sample(node, ((age - b.second) / lifetime).coerceIn(0f, 1f), particleColor)
            val nextDistance = distance + start.distance(end)
            ribbon(node, start, end, from, to, uv(node, index.toFloat() / (points.size - 1), distance), uv(node, (index + 1f) / (points.size - 1), nextDistance))
            distance = nextDistance
        }
        context.progressOverride = null
    }

    private fun sample(node: VfxNodeRuntime, progress: Float, particleColor: FloatArray): Sample {
        node.instance.context.progressOverride = progress
        val surface = config(node)
        val tint = node.sample("tint", surface.tint)
        val channels = IntArray(4) { (tint[it] * particleColor[it]).coerceIn(0f, 1f).times(255f).toInt() }
        return Sample(node.sample("width", surface.width), channels[3] shl 24 or (channels[0] shl 16) or (channels[1] shl 8) or channels[2])
    }

    private fun uv(node: VfxNodeRuntime, progress: Float, distance: Float): Float {
        val spec = node.spec.properties
        val u = if (spec.string("uvMode", "STRETCH") == "TILE") distance / spec.number("tileLength", 1f).coerceAtLeast(0.001f) else progress
        return u + spec.number("uvScroll") * node.instance.time
    }

    private fun ribbon(node: VfxNodeRuntime, start: Vector3f, end: Vector3f, from: Sample, to: Sample, u0: Float, u1: Float) {
        val direction = Vector3f(end).sub(start)
        if (direction.lengthSquared() <= 0.000001f || (from.width <= 0f && to.width <= 0f)) return
        val side = Vector3f(direction).cross(Vector3f(camera).sub(start))
        if (side.lengthSquared() <= 0.000001f) return
        side.normalize()
        val a = Vector3f(side).mul(from.width.coerceAtLeast(0f) * 0.5f)
        val b = Vector3f(side).mul(to.width.coerceAtLeast(0f) * 0.5f)
        val material = config(node).material
        val uv = material.obj("uv")
        fun u(value: Float) = uv.number("u0") + value * (uv.number("u1", 1f) - uv.number("u0"))
        emit(node, material, Vector3f(start).add(end).mul(0.5f), listOf(
            VfxVertex(Vector3f(start).sub(a), u(u0), uv.number("v1", 1f), from.color),
            VfxVertex(Vector3f(start).add(a), u(u0), uv.number("v0"), from.color),
            VfxVertex(Vector3f(end).add(b), u(u1), uv.number("v0"), to.color),
            VfxVertex(Vector3f(end).sub(b), u(u1), uv.number("v1", 1f), to.color)
        ))
    }
}
