package com.algorithmlx.ecr.api.particle

import com.algorithmlx.ecr.api.molang.compiler.eval
import com.algorithmlx.ecr.api.molang.runtime.MolangContext
import com.algorithmlx.ecr.api.molang.runtime.Query
import com.algorithmlx.ecr.api.molang.runtime.Variables
import com.algorithmlx.ecr.api.molang.runtime.VariablesMap
import com.algorithmlx.ecr.api.particle.file.BedrockParticleFile
import net.minecraft.sounds.SoundSource
import org.joml.Quaternionf
import org.joml.Vector3f

class ParticleEmitter(
    val system: ParticleSystem,
    val effect: ParticleEffect,
    val sourceEntity: Query,
    position: Vector3f,
    rotation: Quaternionf,
    velocity: Vector3f,
    val transform: Transform?,
    val offset: Vector3f? = null
) {
    val position = Vector3f(position)
    val rotation = Quaternionf(rotation)
    val velocity = Vector3f(velocity)
    val particles = arrayListOf<BedrockParticle>()

    private val components = effect.components
    private val curveVariables: CurveVariables = CurveVariables({ context }, effect.curves)
    private val variables: Variables = VariablesMap().fallbackBackTo(curveVariables)
    val context: MolangContext = MolangContext(sourceEntity, variables)

    private var firedCreationEvents = false
    private var firedExpirationEvents = false
    private var emissionStopped = false
    private var age: Float by variables.getOrPut("emitter_age", 0f)
    private var activeTime: Float by variables.getOrPut("emitter_lifetime", 0f)
    private var sleepTime = 0f
    private var maxParticles = 0f
    private var cooldown = 0f
    private var nextTimelineEvent: Map.Entry<Float, List<String>>? = null

    init {
        variables["entity_scale"] = 1f
        components.emitterInitialization?.creationExpression?.eval(context)
    }

    fun startLoop(timeSince: Float) {
        if (emissionStopped) return

        for (i in 1..4) variables["emitter_random_$i"] = system.random.nextFloat()

        age = 0f
        activeTime = components.emitterLifetimeLooping?.activeTime?.eval(context)
            ?: components.emitterLifetimeOnce?.activeTime?.eval(context)
            ?: Float.POSITIVE_INFINITY
        sleepTime = components.emitterLifetimeLooping?.sleepTime?.eval(context) ?: 0f

        repeat((components.emitterRateInstant?.numParticles?.eval(context) ?: 0f).toInt().coerceAtLeast(0)) {
            emit(timeSince)
        }

        maxParticles = components.emitterRateSteady?.maxParticles?.eval(context) ?: Float.POSITIVE_INFINITY
        cooldown = spawnInterval()

        if (!firedCreationEvents) {
            firedCreationEvents = true
            fire(timeSince, components.emitterLifetimeEvents.creationEvents, null)
        }
        nextTimelineEvent = components.emitterLifetimeEvents.timeline.lowestEntry()
    }

    fun stopLoop() {
        emissionStopped = true
    }

    fun update(dt: Float): Boolean {
        val safeDt = dt.coerceAtLeast(0f)
        if (!emissionStopped && !updateEmitter(safeDt)) {
            emissionStopped = true
        }

        if (emissionStopped && !firedExpirationEvents) {
            firedExpirationEvents = true
            fire(0f, components.emitterLifetimeEvents.expirationEvents, null)
        }

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            if (!particle.update(safeDt)) {
                onRemove(particle)
                iterator.remove()
            }
        }
        return !emissionStopped || particles.isNotEmpty()
    }

    private fun updateEmitter(dt: Float): Boolean {
        age += dt
        curveVariables.update()
        components.emitterInitialization?.perUpdateExpression?.eval(context)

        transform?.let {
            position.set(it.position)
            offset?.let { localOffset -> position.add(Vector3f(localOffset).rotate(it.rotation)) }
            rotation.set(it.rotation)
            velocity.set(it.velocity)
            if (!it.isValid) return false
        }

        fireTimelineEvents()

        components.emitterLifetimeExpression?.let {
            if (it.expirationExpression.eval(context) != 0f) return false
            if (it.activationExpression.eval(context) == 0f) return true
        }

        if (age > activeTime) {
            if (components.emitterLifetimeOnce != null) return false
            val loopTime = age - activeTime - sleepTime
            if (loopTime < 0f) return true
            startLoop(loopTime)
        }

        cooldown -= dt
        while (cooldown < 0f) {
            if (particles.size >= maxParticles) {
                cooldown = 0f
                break
            }
            val emitTime = -cooldown
            age -= emitTime
            emit(emitTime)
            cooldown += spawnInterval()
            age += emitTime
            if (!cooldown.isFinite()) break
        }
        return true
    }

    private fun spawnInterval(): Float {
        val rate = components.emitterRateSteady?.spawnRate?.eval(context) ?: return Float.POSITIVE_INFINITY
        return if (rate > 0f) 1f / rate else Float.POSITIVE_INFINITY
    }

    internal fun emit(dt: Float, inheritVelocity: Boolean = false) {
        val localSpace = if (components.emitterLocalSpace?.position == true) transform else null
        val particle = BedrockParticle(this, localSpace)
        particle.emit(inheritVelocity)
        addParticle(particle)
        if (!particle.update(dt)) {
            onRemove(particle)
            particles.remove(particle)
        }
    }

    private fun addParticle(particle: BedrockParticle) {
        particles += particle
        val renderPass = effect.renderPass ?: return
        if (components.particleAppearanceBillboard == null) return
        system.billboardRenderPasses.getOrPut(renderPass, ::linkedSetOf).add(particle)
    }

    fun onRemove(particle: BedrockParticle) {
        val renderPass = effect.renderPass ?: return
        val renderPassSet = system.billboardRenderPasses[renderPass] ?: return
        renderPassSet.remove(particle)
        if (renderPassSet.isEmpty()) system.billboardRenderPasses.remove(renderPass)
    }

    internal fun dispose() {
        particles.forEach(::onRemove)
        particles.clear()
    }

    private fun fireTimelineEvents() {
        while (true) {
            val entry = nextTimelineEvent ?: return
            val timeSinceEvent = age - entry.key
            if (timeSinceEvent < 0f) return
            fire(timeSinceEvent, entry.value, null)
            nextTimelineEvent = components.emitterLifetimeEvents.timeline.higherEntry(entry.key)
        }
    }

    fun fire(timeSince: Float, events: List<String>, particle: BedrockParticle?) =
        events.forEach { fire(timeSince, it, particle) }

    fun fire(timeSince: Float, eventName: String, particle: BedrockParticle?) {
        effect.events[eventName]?.let { fire(timeSince, it, particle) }
    }

    fun fire(timeSince: Float, event: BedrockParticleFile.Event, particle: BedrockParticle?) {
        event.sequence?.forEach { fire(timeSince, it, particle) }

        event.randomize?.let { options ->
            var choice = system.random.nextDouble() * options.sumOf { it.weight.toDouble() }
            for ((weight, value) in options) {
                choice -= weight
                if (choice <= 0.0) {
                    fire(timeSince, value, particle)
                    break
                }
            }
        }

        event.expression?.let {
            age -= timeSince
            it.eval(context)
            age += timeSince
        }

        event.particle?.let { config ->
            val targetEffect = effect.referencedEffects[config.effect] ?: return@let
            val particlePosition = particle?.globalPosition ?: position
            val particleVelocity = particle?.globalVelocity ?: velocity
            val bound = config.type.isBound && transform != null
            val targetEmitter = ParticleEmitter(
                system = system,
                effect = targetEffect,
                sourceEntity = sourceEntity,
                position = particlePosition,
                rotation = if (bound) rotation else Quaternionf(),
                velocity = particleVelocity,
                transform = if (bound) transform else null,
                offset = if (bound) {
                    particle?.globalPosition
                        ?.sub(transform.position, Vector3f())
                        ?.rotate(Quaternionf(transform.rotation).invert())
                        ?: offset
                } else null
            )
            config.preEffectExpression.eval(targetEmitter.context)
            if (config.type.isParticle) {
                targetEmitter.emit(timeSince, config.type.inheritVelocity)
            } else {
                system.addEmitter(targetEmitter)
                targetEmitter.startLoop(timeSince)
                targetEmitter.update(timeSince)
            }
        }

        event.sound?.let { config ->
            val sound = effect.referencedSounds[config.eventName] ?: return@let
            val soundPosition = particle?.globalPosition ?: position
            system.level.playLocalSound(
                soundPosition.x.toDouble(),
                soundPosition.y.toDouble(),
                soundPosition.z.toDouble(),
                sound,
                SoundSource.AMBIENT,
                1f,
                1f,
                false
            )
        }
    }
}
