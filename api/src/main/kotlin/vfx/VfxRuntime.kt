package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.particle.collision.LevelCollisionProvider
import com.algorithmlx.ecr.api.particle.collision.CollisionProvider
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.random.Random

class VfxParticle internal constructor(
    val position: Vector3f,
    val velocity: Vector3f,
    val lifetime: Float,
    val random: Float,
    val parentRandom: Float,
    val local: Boolean
) {
    val previous = Vector3f(position)
    val rotation = Quaternionf()
    val scale = Vector3f(1f)
    var age = 0f
    var collided = false
    val trail = ArrayDeque<Pair<Vector3f, Float>>()

    val progress: Float get() = (age / lifetime).coerceIn(0f, 1f)
}

class VfxInstance(
    val effect: VfxEffect,
    position: Vec3,
    val level: ClientLevel? = null,
    val data: MutableMap<String, Float> = linkedMapOf(),
    seed: Int = Random.nextInt(),
    private val collision: CollisionProvider = level?.let(::LevelCollisionProvider) ?: CollisionProvider { _, _, _ -> null }
) {
    val random = Random(seed)
    val context = VfxContext(random, data)
    internal val renderer by lazy { VfxRenderer(this) }
    private val roots = effect.nodes.map { VfxNodeRuntime(this, it, null) }
    private val nodes = roots.flatMap { it.walk() }
    private val emitters = nodes.mapNotNull { it.emitter }
    var position = position
        private set
    var time = 0f
        private set
    var stopping = false
        private set
    var immediate = false
        private set
    var maxParticles = 8000
    var entity: Entity? = null
        set(value) {
            field = value
            attachmentOffset = value?.let { position.subtract(it.position()) } ?: Vec3.ZERO
        }
    internal var attachmentId: Int? = null
    private var attachmentOffset = Vec3.ZERO
    private var lastPosition = position
    val velocity = Vector3f()

    val isFinished: Boolean
        get() = if (immediate) true else when {
            stopping -> emitters.all { it.particles.isEmpty() }
            effect.timeline.loop -> false
            else -> time >= effect.timeline.duration && emitters.all { it.particles.isEmpty() }
        }

    val particleCount: Int get() = emitters.sumOf { it.particles.size }

    fun node(id: String): VfxNodeRuntime? = nodes.firstOrNull { it.spec.id == id }

    fun moveTo(next: Vec3) {
        val delta = Vector3f((position.x - next.x).toFloat(), (position.y - next.y).toFloat(), (position.z - next.z).toFloat())
        emitters.forEach { emitter ->
            emitter.particles.filterNot { it.local }.forEach {
                it.position.add(delta)
                it.previous.add(delta)
                it.trail.forEach { point -> point.first.add(delta) }
            }
        }
        nodes.filter { it.spec.type == "trail" && it.particleOwner == null }
            .forEach { node -> node.trail.forEach { it.first.add(delta) } }
        position = next
    }

    fun stop(immediate: Boolean = false) {
        stopping = true
        this.immediate = immediate
        if (immediate) emitters.forEach { it.particles.clear() }
    }

    fun restart() {
        time = 0f
        stopping = false
        immediate = false
        emitters.forEach { it.restart() }
        nodes.forEach { it.trail.clear() }
        lastPosition = position
        velocity.zero()
        update(0f)
    }

    fun update(dt: Float) {
        if (immediate) return
        val step = dt.coerceIn(0f, 0.25f)
        if (entity == null) attachmentId?.let { id -> level?.getEntity(id)?.let { entity = it } }
        entity?.takeIf { it.isAlive }?.let { moveTo(it.position().add(attachmentOffset)) }
        if (step > 0f) {
            velocity.set(
                ((position.x - lastPosition.x) / step).toFloat(),
                ((position.y - lastPosition.y) / step).toFloat(),
                ((position.z - lastPosition.z) / step).toFloat()
            )
        }
        lastPosition = position
        time += step
        if (!effect.timeline.loop && time >= effect.timeline.duration) stopping = true
        context.effectTime = time
        context.particleActive = false
        roots.forEach { it.place(Matrix4f()) }
        nodes.forEach { it.updateTrail(time) }
        emitters.forEach { it.update(step) }
    }

    internal fun collide(particle: VfxParticle, motion: Vector3f, radius: Float, localMatrix: Matrix4f?): Pair<Vector3f, Vector3f>? {
        val world = Vector3f(particle.position)
        val displacement = Vector3f(motion)
        if (localMatrix != null) {
            localMatrix.transformPosition(world)
            localMatrix.transformDirection(displacement)
        }
        world.add(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
        val hit = collision.query(world, radius, displacement) ?: return null
        if (localMatrix == null) return hit
        val inverse = Matrix4f(localMatrix).invert()
        return inverse.transformDirection(hit.first) to inverse.transformDirection(hit.second).normalize()
    }

    internal fun timeline(node: VfxNode, property: String, channel: Int, default: Float): Float {
        val track = effect.tracks[node.id to property] ?: return default
        val curve = track.curves.firstOrNull { it.channel == channel && it.visible && it.curve.keys.isNotEmpty() } ?: return default
        val local = if (effect.timeline.loop && effect.timeline.duration > 0f) time % effect.timeline.duration else time
        return curve.curve.sample(local)
    }
}

class VfxNodeRuntime internal constructor(
    val instance: VfxInstance,
    val spec: VfxNode,
    val parent: VfxNodeRuntime?
) {
    val children = spec.children.map { VfxNodeRuntime(instance, it, this) }
    val emitter = if (spec.type == "emitter") VfxEmitter(this) else null
    internal val shake = if (spec.type == "camera_shake") VfxCameraShake(spec) else null
    val matrix = Matrix4f()
    val trail = ArrayDeque<Pair<Vector3f, Float>>()
    var active = true
        private set

    fun walk(): List<VfxNodeRuntime> = listOf(this) + children.flatMap { it.walk() }

    internal val particleOwner: VfxNodeRuntime?
        get() = generateSequence(parent) { it.parent }.firstOrNull { it.emitter != null }

    internal fun sample(property: String, value: VfxValue, channel: Int = 0): Float =
        instance.timeline(spec, property, channel, value.sample(instance.context))

    internal fun sample(property: String, value: VfxVector): Vector3f = value.sample(instance.context).also {
        it.set(instance.timeline(spec, property, 0, it.x), instance.timeline(spec, property, 1, it.y), instance.timeline(spec, property, 2, it.z))
    }

    internal fun sample(property: String, value: VfxColor): FloatArray = value.sample(instance.context).also { color ->
        color.indices.forEach { color[it] = instance.timeline(spec, property, it, color[it]) }
    }

    internal fun updateTrail(time: Float) {
        if (spec.type != "trail" || particleOwner != null || !active) return
        val point = matrix.transformPosition(Vector3f())
        val minDistance = spec.properties.number("minDistance", 0.05f).coerceAtLeast(0f)
        if (trail.firstOrNull()?.first?.distanceSquared(point)?.let { it >= minDistance * minDistance } != false) {
            trail.addFirst(point to time)
        }
        val lifetime = spec.properties.number("lifetime", 0.5f).coerceAtLeast(0f)
        val maximum = spec.properties.number("maxPoints", 32f).toInt().coerceIn(2, 128)
        while (trail.size > maximum || trail.lastOrNull()?.second?.let { time - it > lifetime } == true) trail.removeLast()
    }

    internal fun place(parentMatrix: Matrix4f) {
        val transform = spec.transform
        val enabled = instance.timeline(spec, "enabled", 0, if (spec.enabled) 1f else 0f) > 0.5f
        active = enabled && (parent?.active ?: true)
        matrix.set(parentMatrix)
            .translate(
                instance.timeline(spec, "transform.position", 0, transform.position.x),
                instance.timeline(spec, "transform.position", 1, transform.position.y),
                instance.timeline(spec, "transform.position", 2, transform.position.z)
            )
            .rotateXYZ(
                Math.toRadians(instance.timeline(spec, "transform.rotation", 0, transform.rotation.x).toDouble()).toFloat(),
                Math.toRadians(instance.timeline(spec, "transform.rotation", 1, transform.rotation.y).toDouble()).toFloat(),
                Math.toRadians(instance.timeline(spec, "transform.rotation", 2, transform.rotation.z).toDouble()).toFloat()
            )
            .scale(
                instance.timeline(spec, "transform.scale", 0, transform.scale.x),
                instance.timeline(spec, "transform.scale", 1, transform.scale.y),
                instance.timeline(spec, "transform.scale", 2, transform.scale.z)
            )
        children.forEach { it.place(matrix) }
    }
}

