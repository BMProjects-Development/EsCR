package com.algorithmlx.ecr.api.vfx

import com.mojang.blaze3d.vertex.PoseStack
import com.algorithmlx.ecr.api.vfx.gltf.VfxGltfAssets
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.LevelRenderState
import net.minecraft.client.renderer.state.level.CameraRenderState
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import com.algorithmlx.ecr.api.LOGGER
import java.util.concurrent.atomic.AtomicInteger
import org.joml.Quaternionf
import kotlin.math.PI
import kotlin.math.sin

object VfxScenes {
    private val nextHandle = AtomicInteger(-1)
    private var level: ClientLevel? = null
    private val instances = linkedMapOf<Int, VfxInstance>()

    var particleLimit = 8000
        set(value) {
            require(value >= 0)
            field = value
        }

    val activeHandles: Set<Int> get() = instances.keys.toSet()

    internal fun instances(): List<VfxInstance> = if (level === Minecraft.getInstance().level) instances.values.toList() else emptyList()

    fun play(id: Identifier, position: Vec3, entity: Entity? = null, data: Map<String, Float> = emptyMap()): Int? {
        val handle = nextHandle.getAndDecrement()
        return if (play(handle, id, position, entity, data) != null) handle else null
    }

    internal fun play(handle: Int, id: Identifier, position: Vec3, entity: Entity? = null, data: Map<String, Float> = emptyMap()): VfxInstance? {
        val current = Minecraft.getInstance().level ?: return null
        if (level !== current) clear()
        level = current
        val effect = VfxAssets[id] ?: run {
            LOGGER.warn("Unable to play VFX {}: effect is not loaded", id)
            return null
        }
        return VfxInstance(effect, position, current, data.toMutableMap()).also {
            it.entity = entity
            it.maxParticles = (particleLimit - instances.values.sumOf(VfxInstance::particleCount)).coerceAtLeast(0)
            instances[handle] = it
            it.update(0f)
        }
    }

    fun get(handle: Int): VfxInstance? = instances[handle]

    fun stop(handle: Int, immediate: Boolean = false) {
        instances[handle]?.stop(immediate)
        if (immediate) instances.remove(handle)
    }

    fun move(handle: Int, position: Vec3) {
        instances[handle]?.moveTo(position)
    }

    fun data(handle: Int, values: Map<String, Float>) {
        instances[handle]?.data?.putAll(values)
    }

    fun tick() {
        if (Minecraft.getInstance().isPaused) return
        val current = Minecraft.getInstance().level
        if (current == null || level !== current) {
            clear()
            return
        }
        val share = if (instances.isEmpty()) particleLimit else (particleLimit / instances.size).coerceAtLeast(0)
        val iterator = instances.iterator()
        while (iterator.hasNext()) {
            val (_, instance) = iterator.next()
            instance.maxParticles = share
            instance.update(1f / 20f)
            if (instance.isFinished || instance.entity?.isAlive == false) iterator.remove()
        }
    }

    fun submit(poseStack: PoseStack, collector: SubmitNodeCollector, state: LevelRenderState, partialTick: Float) {
        VfxGpuGeometry.beginFrame()
        if (level !== Minecraft.getInstance().level) return
        instances.values.forEach {
            it.context.partialTick = partialTick
            it.context.gameTime = ((it.level?.gameTime ?: 0L) + partialTick) / 20f
            it.renderer.submit(poseStack, collector, state, partialTick)
        }
    }

    fun shakeCamera(state: CameraRenderState) {
        if (level !== Minecraft.getInstance().level || !state.initialized) return
        val rotation = Quaternionf()
        var changed = false
        instances.values.forEach { instance ->
            if (instance.stopping) return@forEach
            instance.effect.nodes.forEach { root ->
                instance.node(root.id)?.walk()?.forEach { node ->
                    if (!node.active || node.spec.type != "camera_shake") return@forEach
                    val shake = node.shake ?: return@forEach
                    val context = instance.context
                    context.useEffect(instance.time, instance.effect.timeline.duration, instance.effect.timeline.loop)
                    val origin = node.matrix.transformPosition(Vector3f())
                    context.cameraDistance = state.pos.distanceTo(instance.position.add(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble())).toFloat()
                    val strength = node.sample("shake.strength", shake.strength).coerceIn(0f, 4f)
                    if (strength <= 0f) return@forEach
                    val frequency = node.sample("shake.frequency", shake.frequency).coerceAtLeast(0f)
                    val amplitude = shake.amplitude
                    val time = instance.time + state.cameraEntityPartialTicks / 20f
                    val pitch = sin(time * frequency * 2f * PI.toFloat()) * amplitude.x * strength
                    val yaw = sin(time * frequency * 2.37f * PI.toFloat()) * amplitude.y * strength
                    val roll = sin(time * frequency * 1.73f * PI.toFloat()) * amplitude.z * strength
                    rotation.mul(Quaternionf().rotationXYZ(Math.toRadians(pitch.toDouble()).toFloat(), Math.toRadians(yaw.toDouble()).toFloat(), Math.toRadians(roll.toDouble()).toFloat()))
                    changed = true
                }
            }
        }
        if (changed) {
            state.orientation.mul(rotation)
            state.viewRotationMatrix.rotation(Quaternionf(state.orientation).conjugate())
        }
    }

    fun clear() {
        instances.clear()
        VfxScreenEffects.clear()
        VfxGpuGeometry.clear()
        VfxGltfAssets.clear()
        level = null
    }
}
