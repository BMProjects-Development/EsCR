package com.algorithmlx.ecr.api.vfx

import org.joml.Matrix4f
import kotlinx.serialization.json.JsonObject
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.math.tan
import java.util.IdentityHashMap

class VfxEmitter internal constructor(val node: VfxNodeRuntime) {
    private val instance = node.instance
    private val spec = node.spec.properties
    private val hollow = spec.string("type").startsWith("hollowengine:")
    private val emission = spec.obj("emission")
    private val shape = spec.obj("shape")
    private val spawn = spec.obj("spawn")
    private val motion = spec.obj("motion")
    private val modules = spec.array("modules").map { it.obj() }.filter { it.bool("enabled", true) }.map { VfxModule(node, it) }
    private val subEmission = spec.obj("subEmission")
    private val subCount = subEmission["count"].value(12f)
    private val subInheritVelocity = subEmission["inheritVelocity"].value()
    private val rate = emission["rate"].value(20f)
    private val gravity = motion["gravity"].value()
    private val drag = motion["drag"].value()
    private val lifetime = spawn["lifetime"].value(1f)
    private val speed = spawn["speed"].value(1f)
    private val offset = spawn["offset"].valueVector()
    private val inheritVelocity = spawn["inheritVelocity"].value()
    private val radius = shape["radius"].value(0.5f)
    private val extents = shape["extents"].valueVector(0.5f)
    private val angle = shape["angle"].value(25f)
    private val thickness = shape["thickness"].value(1f)
    private val direction = shape.vector("direction").takeIf { it.lengthSquared() > 0f } ?: Vector3f(0f, 1f, 0f)
    private val modelSurface by lazy { VfxModelSurface.load(shape.string("model")) }
    private val modelNormal = Vector3f(0f, 1f, 0f)
    private val maxCount = emission.number("maxParticles", 256f).toInt().coerceIn(0, 100000)
    private val burst = emission.number("burst").toInt().coerceIn(0, 100000)
    private val duration = emission.number("duration", 2f).coerceAtLeast(0f)
    private val loop = emission.bool("loop", true)
    private val loopDelay = emission.number("loopDelay").coerceAtLeast(0f)
    private val local = spec.string("space", "WORLD") == "LOCAL"
    private val childEmitters by lazy { node.children.flatMap { it.walk() }.mapNotNull { it.emitter }
        .filter { it.hasSubEmission && it.node.particleOwner === node }
    }
    private val trails by lazy { node.children.flatMap { it.walk() }.filter { it.spec.type == "trail" && it.particleOwner === node } }
    private val trailLifetime by lazy { trails.maxOfOrNull { it.spec.properties.number("lifetime", 0.5f).coerceAtLeast(0.001f) } ?: 0f }
    private val trailPoints by lazy { trails.maxOfOrNull { it.spec.properties.number("maxPoints", 32f).toInt().coerceIn(2, 128) } ?: 0 }
    internal val hasSubEmission get() = spec["subEmission"] is JsonObject && node.particleOwner != null
    val particles = ArrayList<VfxParticle>()
    var age = 0f
        private set
    private var carry = 0f
    private val aliveCarry = IdentityHashMap<VfxParticle, Float>()
    private var burstDone = false
    private var completed = false
    private var spawned = 0
    private var cachedOrigin = Vector3f()
    val isFinished get() = completed && particles.isEmpty()

    fun restart() {
        spawned = 0
        particles.clear()
        age = 0f
        carry = 0f
        aliveCarry.clear()
        burstDone = false
        completed = false
    }

    fun update(dt: Float) {
        if (!node.active && particles.isEmpty()) return
        cachedOrigin = node.matrix.transformPosition(Vector3f())
        particles.forEach { it.previous.set(it.position) }
        val steps = ceil(dt / spec.number("maxStep", 1f / 60f).coerceIn(1f / 240f, 0.25f)).toInt().coerceIn(1, 32)
        val step = dt / steps
        repeat(steps) { updateStep(step) }
        if (hasSubEmission && node.particleOwner?.emitter?.isFinished == true && particles.isEmpty()) completed = true
    }

    private fun updateStep(dt: Float) {
        val context = instance.context
        context.useEffect(instance.time, instance.effect.timeline.duration, instance.effect.timeline.loop)
        context.emitterAge = age
        context.particleCount = particles.size
        context.particleActive = false
        if (node.active && !instance.stopping && !hasSubEmission && !completed) {
            if (!burstDone) {
                burstDone = true
                repeat(burst.coerceAtMost(maxCount)) { createParticle(null) }
            }
            if (duration == 0f || age < duration) {
                carry += instance.timeline(node.spec, "emission.rate", 0, rate.sample(context)).coerceIn(0f, 100000f) * dt
                val count = carry.toInt().coerceAtMost(maxCount)
                carry -= count
                repeat(count) { createParticle(null) }
            }
            age += dt
            if (duration > 0f && age >= duration + loopDelay)
                if (loop) {
                    age = 0f
                    burstDone = false
                }
                else
                    completed = true
        }
        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.age += dt
            fillContext(particle)
            if (particle.age >= particle.lifetime) {
                childEmitters.forEach { it.emitFrom(particle, "DEATH", dt) }
                childEmitters.forEach { it.aliveCarry.remove(particle) }
                iterator.remove()
                continue
            }
            val acceleration = Vector3f(0f, -instance.timeline(node.spec, "motion.gravity", 0, gravity.sample(context)), 0f)
            val extraVelocity = Vector3f()
            modules.forEach { module -> applyModule(module, particle, acceleration, extraVelocity, dt) }
            particle.velocity.fma(dt, acceleration)
            particle.velocity.mul(exp((-instance.timeline(node.spec, "motion.drag", 0, drag.sample(context)).coerceAtLeast(0f) * dt).toDouble()).toFloat())
            val displacement = Vector3f(particle.velocity).add(extraVelocity).mul(dt)
            val collisionModule = modules.firstOrNull { it.type == "collision" }
            val hit = if (collisionModule == null)
                null
            else
                instance.collide(
                    particle,
                    displacement,
                    collisionModule.number("radius", 0.05f).coerceAtLeast(0f),
                    if (particle.local) node.matrix else null
                )
            if (hit != null && collisionModule != null) {
                particle.position.add(hit.first)
                particle.collided = true
                childEmitters.forEach { it.emitFrom(particle, "COLLISION", dt) }
                particle.age += collisionModule.number("lifetimeLoss").coerceAtLeast(0f)
                when (collisionModule.string("action", "BOUNCE")) {
                    "DIE" -> particle.age = particle.lifetime
                    "SLIDE" -> particle.velocity.sub(Vector3f(hit.second).mul(particle.velocity.dot(hit.second)))
                    else -> particle.velocity.sub(Vector3f(hit.second).mul((1f + collisionModule.number("bounce", 0.4f)) * particle.velocity.dot(hit.second)))
                }
                particle.velocity.mul(collisionModule.number("friction", 0.8f).coerceIn(0f, 1f))
            }
            else
                particle.position.add(displacement)
            if (particle.age >= particle.lifetime) {
                childEmitters.forEach { it.emitFrom(particle, "DEATH", dt) }
                childEmitters.forEach { it.aliveCarry.remove(particle) }
                iterator.remove()
                continue
            }
            childEmitters.forEach { it.emitFrom(particle, "ALIVE", dt) }
            if (trailPoints > 0) {
                particle.trail.addFirst(Vector3f(particle.position) to particle.age)
                while (particle.trail.size > trailPoints || particle.trail.lastOrNull()?.second?.let { particle.age - it > trailLifetime } == true) particle.trail.removeLast()
            }
        }
        if (instance.stopping && particles.isEmpty()) completed = true
    }

    private fun applyModule(module: VfxModule, particle: VfxParticle, force: Vector3f, velocity: Vector3f, dt: Float) {
        val context = instance.context
        when (module.type) {
            "velocity_over_lifetime" -> {
                val extra = module.vector("velocity")
                val moduleLocal = module.string("space", "LOCAL") == "LOCAL"
                if (moduleLocal && !particle.local) node.matrix.transformDirection(extra)
                if (!moduleLocal && particle.local) Matrix4f(node.matrix).invert().transformDirection(extra)
                velocity.add(extra)
            }
            "force" -> {
                val strength = module.sample("strength", 1f)
                val center = module.vector("center")
                if (!particle.local) node.matrix.transformPosition(center)
                val relative = Vector3f(particle.position).sub(center)
                val radius = module.sample("radius")
                if (radius > 0f && relative.length() > radius) return
                val attenuation = if (radius > 0f && module.bool("falloff", true)) 1f - relative.length() / radius else 1f
                when (module.string("kind", "DIRECTIONAL")) {
                    "POINT" -> if (relative.lengthSquared() > 0.00001f) force.add(relative.normalize().mul(strength * attenuation))
                    "VORTEX" -> {
                        val axis = module.vector("direction").takeIf { it.lengthSquared() > 0f } ?: Vector3f(0f, 1f, 0f)
                        if (!particle.local) node.matrix.transformDirection(axis)
                        val tangent = axis.normalize().cross(relative)
                        if (tangent.lengthSquared() > 0.00001f) force.add(tangent.normalize().mul(strength * attenuation))
                    }
                    "DRAG" -> particle.velocity.mul(exp((-strength.coerceAtLeast(0f) * attenuation * dt).toDouble()).toFloat())
                    else -> {
                        val axis = module.vector("direction").takeIf { it.lengthSquared() > 0f } ?: Vector3f(0f, 1f, 0f)
                        if (!particle.local) node.matrix.transformDirection(axis)
                        force.add(axis.normalize().mul(strength * attenuation))
                    }
                }
            }
            "noise" -> {
                val frequency = module.sample("frequency", 0.5f)
                val strength = module.sample("strength", 1f)
                val scroll = module.number("scrollSpeed", 0.25f) * instance.time
                val octaves = module.number("octaves", 1f).toInt().coerceIn(1, 5)
                val x = particle.position.x * frequency
                val y = particle.position.y * frequency
                val z = particle.position.z * frequency
                force.add(
                    VfxNoise.fractal(x + scroll, y, z, module.noiseSeeds[0], octaves) * strength,
                    VfxNoise.fractal(x, y + scroll, z, module.noiseSeeds[1], octaves) * strength,
                    VfxNoise.fractal(x, y, z + scroll, module.noiseSeeds[2], octaves) * strength
                )
            }
        }
    }

    private fun createParticle(parent: VfxParticle?) {
        if (particles.size >= maxCount || instance.particleCount >= instance.maxParticles) return
        val context = instance.context
        context.parentRandom = parent?.random ?: 0f
        context.particleRandom = instance.random.nextFloat()
        context.particleIndex = spawned.toFloat()
        context.particleActive = false
        context.particleAge = 0f
        context.particleLifetime = 1f
        context.particleSpeed = 0f
        context.position = Vector3f()
        context.velocity = Vector3f()
        val location = shapePosition(context)
        val launch = when (shape.string("directionMode", "SHAPE")) {
            "RANDOM" -> randomUnit()
            "FIXED" -> Vector3f(direction).normalize()
            else -> when (shape.string("kind", "POINT")) {
                "CONE" -> {
                    val spread = tan(Math.toRadians(angle.sample(context).coerceIn(0f, 89f).toDouble())).toFloat()
                    val outward = if (location.lengthSquared() > 0.000001f) Vector3f(location).normalize().mul(spread * instance.random.nextFloat()) else Vector3f()
                    outward.add(0f, 1f, 0f).normalize()
                }
                "MODEL" -> Vector3f(modelNormal)
                "BOX", "LINE", "POINT" -> Vector3f(direction).normalize()
                else -> if (location.lengthSquared() > 0.000001f) Vector3f(location).normalize() else Vector3f(direction).normalize()
            }
        }
        val base = Vector3f(location).add(node.sample("spawn.offset", offset))
        if (parent != null) {
            val source = node.particleOwner?.emitter
            val parentPosition = Vector3f(parent.position)
            if (parent.local) source?.node?.matrix?.transformPosition(parentPosition)
            if (source != null) parentPosition.add(node.matrix.getTranslation(Vector3f()).sub(source.node.matrix.getTranslation(Vector3f())))
            if (local) {
                Matrix4f(node.matrix).invert().transformPosition(parentPosition)
                base.add(parentPosition)
            } else {
                node.matrix.transformDirection(base)
                base.add(parentPosition)
            }
        }
        else if (!local) node.matrix.transformPosition(base)
        if (!local) node.matrix.transformDirection(launch).normalize()
        val velocity = launch.mul(node.sample("spawn.speed", speed))
        val inherited = if (parent == null)
            Vector3f(instance.velocity)
        else
            Vector3f(parent.velocity).also {
                val source = node.particleOwner?.emitter
                if (parent.local) source?.node?.matrix?.transformDirection(it)
                if (local) Matrix4f(node.matrix).invert().transformDirection(it)
            }
        if (parent == null && local) Matrix4f(node.matrix).invert().transformDirection(inherited)
        val inheritedFraction = if (parent == null) node.sample("spawn.inherit_velocity", inheritVelocity) else subInheritVelocity.sample(context)
        velocity.fma(inheritedFraction.coerceIn(0f, 1f), inherited)
        val particle = VfxParticle(base, velocity, node.sample("spawn.lifetime", lifetime).coerceAtLeast(0.001f), context.particleRandom, context.parentRandom, local)
        particle.index = spawned++
        if (spec.bool("inheritRotation", true)) node.matrix.getUnnormalizedRotation(particle.rotation)
        if (spec.bool("inheritScale", false)) node.matrix.getScale(particle.scale)
        particle.trail.addFirst(Vector3f(base) to 0f)
        particles += particle
        childEmitters.forEach { it.emitFrom(particle, "SPAWN", 0f) }
    }

    private fun shapePosition(context: VfxContext): Vector3f {
        val random = instance.random
        val r = node.sample("shape.radius", radius).coerceAtLeast(0f)
        return when (shape.string("kind", "POINT")) {
            "SPHERE" -> {
                val fraction = node.sample("shape.thickness", thickness).coerceIn(0f, 1f)
                val direction = randomUnit()
                val inner = 1f - fraction
                val reach = if (hollow) 1f - random.nextFloat() * fraction else (inner * inner * inner + (1f - inner * inner * inner) * random.nextFloat()).toDouble().pow(1.0 / 3.0).toFloat()
                direction.mul(r * reach)
            }
            "BOX" -> node.sample("shape.extents", extents).mul(random.nextFloat() * 2f - 1f, random.nextFloat() * 2f - 1f, random.nextFloat() * 2f - 1f)
            "DISC" -> {
                val t = random.nextFloat() * PI.toFloat() * 2f
                val reach = r * sqrt(random.nextFloat())
                Vector3f(cos(t), 0f, sin(t)).mul(if (hollow) reach * (1f - random.nextFloat() * node.sample("shape.thickness", thickness).coerceIn(0f, 1f)) else reach)
            }
            "LINE" -> node.sample("shape.extents", extents).mul(random.nextFloat())
            "CONE" -> {
                val t = random.nextFloat() * PI.toFloat() * 2f
                val distance = sqrt(random.nextFloat()) * r * if (hollow) (1f - random.nextFloat() * node.sample("shape.thickness", thickness).coerceIn(0f, 1f)) else 1f
                Vector3f(cos(t) * distance, 0f, sin(t) * distance)
            }
            "MODEL" -> modelSurface?.sample(random)?.let { (point, normal) ->
                modelNormal.set(normal)
                point
            } ?: Vector3f()
            else -> Vector3f()
        }
    }

    private fun randomUnit(): Vector3f {
        val z = instance.random.nextFloat() * 2f - 1f
        val angle = instance.random.nextFloat() * PI.toFloat() * 2f
        val horizontal = sqrt(1f - z * z)
        return Vector3f(cos(angle) * horizontal, z, sin(angle) * horizontal)
    }

    private fun fillContext(particle: VfxParticle) {
        val context = instance.context
        context.progressOverride = null
        context.particleAge = particle.age
        context.particleActive = true
        context.particleLifetime = particle.lifetime
        context.particleRandom = particle.random
        context.particleIndex = particle.index.toFloat()
        context.parentRandom = particle.parentRandom
        context.particleSpeed = particle.velocity.length()
        context.position = particle.position
        context.velocity = particle.velocity
        context.emitterAge = age
        context.particleCount = particles.size
    }

    private fun emitFrom(parent: VfxParticle, event: String, dt: Float) {
        if (!node.active || instance.stopping || subEmission.string("event", "DEATH") != event) return
        fillContext(parent)
        val count = subCount.sample(instance.context).coerceIn(0f, 10000f)
        val number = if (event == "ALIVE") {
            val owed = aliveCarry.getOrDefault(parent, 0f) + count * dt
            val whole = owed.toInt()
            aliveCarry[parent] = owed - whole
            whole
        }
        else
            count.toInt()
        repeat(number.coerceAtMost(maxCount)) { createParticle(parent) }
    }
}
