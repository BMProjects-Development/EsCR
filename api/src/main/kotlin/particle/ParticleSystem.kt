package com.algorithmlx.ecr.api.particle

import com.algorithmlx.ecr.api.molang.runtime.Query
import com.algorithmlx.ecr.api.particle.collision.CollisionProvider
import com.algorithmlx.ecr.api.particle.collision.LevelCollisionProvider
import com.algorithmlx.ecr.api.particle.file.BedrockParticleFile
import com.algorithmlx.ecr.api.particle.light.LevelLightProvider
import com.algorithmlx.ecr.api.particle.light.LightProvider
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.LevelRenderState
import net.minecraft.world.level.Level
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*

class ParticleSystem(
    val level: Level,
    val random: Random,
    val collisionProvider: CollisionProvider,
    val lightProvider: LightProvider,
) {
    private val emitters = mutableListOf<ParticleEmitter>()
    private val pendingEmitters = mutableListOf<ParticleEmitter>()
    private var updating = false

    internal val billboardRenderPasses = linkedMapOf<ParticleEffect.RenderPass, MutableSet<BedrockParticle>>()

    companion object {
        fun create(level: Level) = ParticleSystem(
            level,
            Random(),
            LevelCollisionProvider(level),
            LevelLightProvider(level),
        )
    }

    fun remove(identifier: String) {
        removeMatching(emitters) { it.effect.identifier == identifier }
        removeMatching(pendingEmitters) { it.effect.identifier == identifier }
    }

    fun remove(emitter: ParticleEmitter) {
        if (emitters.remove(emitter) || pendingEmitters.remove(emitter)) emitter.dispose()
    }

    fun spawn(
        effect: BedrockParticleFile,
        query: Query = Query.EMPTY,
        transform: Transform = Transform.Zero,
    ) = spawn(ParticleEffect.fromFile(effect), query, transform)

    fun spawn(
        effect: ParticleEffect,
        query: Query = Query.EMPTY,
        transform: Transform = Transform.Zero,
    ): ParticleEmitter {
        val emitter = ParticleEmitter(
            this,
            effect,
            query,
            transform.position,
            transform.rotation,
            transform.velocity,
            transform,
        )
        addEmitter(emitter)
        emitter.startLoop(0f)
        emitter.update(0f)
        return emitter
    }

    internal fun addEmitter(emitter: ParticleEmitter) {
        if (updating) pendingEmitters += emitter else emitters += emitter
    }

    fun update(dt: Float = 1f / 20f) {
        updating = true
        try {
            val iterator = emitters.iterator()
            while (iterator.hasNext()) {
                val emitter = iterator.next()
                if (!emitter.update(dt)) {
                    emitter.dispose()
                    iterator.remove()
                }
            }
        } finally {
            updating = false
            emitters += pendingEmitters
            pendingEmitters.clear()
        }
    }

    fun submit(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        levelRenderState: LevelRenderState,
        partialTick: Float,
        cameraUuid: UUID?,
        firstPerson: Boolean,
    ) {
        if (billboardRenderPasses.isEmpty()) return
        val renderProgress = partialTick.coerceIn(0f, 1f)
        val camera = levelRenderState.cameraRenderState
        val cameraPosition = Vector3f(camera.pos.x.toFloat(), camera.pos.y.toFloat(), camera.pos.z.toFloat())
        val cameraRotation = Quaternionf(camera.orientation)
        val cameraFacing = Vector3f(0f, 0f, -1f).rotate(cameraRotation)
        val renderDistance = Minecraft.getInstance().options.effectiveRenderDistance * 16F
        val renderDistanceSquare = renderDistance * renderDistance

        poseStack.pushPose()
        poseStack.translate(-camera.pos.x, -camera.pos.y, -camera.pos.z)
        try {
            billboardRenderPasses.entries
                .sortedBy { it.key.material.needsSorting }
                .forEach { (renderPass, particles) ->
                    var quads = particles.asSequence()
                        .mapNotNull { particle ->
                            val worldPosition = particle.interpolatedGlobalPosition(renderProgress)
                            if (worldPosition.distanceSquared(cameraPosition) > renderDistanceSquare) return@mapNotNull null
                            if (!camera.cullFrustum.pointInFrustum(
                                    worldPosition.x.toDouble(),
                                    worldPosition.y.toDouble(),
                                    worldPosition.z.toDouble(),
                                )
                            ) return@mapNotNull null

                            particle.extractBillboard(
                                worldPosition,
                                renderProgress,
                                cameraPosition,
                                cameraRotation,
                                cameraFacing,
                                cameraUuid,
                                firstPerson,
                            )
                        }
                        .toList()
                    if (renderPass.material.needsSorting) quads = quads.sortedByDescending(ParticleQuad::distance)
                    if (quads.isEmpty()) return@forEach

                    collector.submitCustomGeometry(poseStack, renderPass.renderType) { pose, consumer ->
                        quads.forEach { it.render(pose, consumer) }
                    }
                }
        } finally {
            poseStack.popPose()
        }
    }

    fun isEmpty() = emitters.isEmpty() && pendingEmitters.isEmpty()
    fun hasAnythingToRender() = billboardRenderPasses.isNotEmpty()

    private inline fun removeMatching(
        source: MutableList<ParticleEmitter>,
        predicate: (ParticleEmitter) -> Boolean,
    ) {
        val iterator = source.iterator()
        while (iterator.hasNext()) {
            val emitter = iterator.next()
            if (predicate(emitter)) {
                emitter.dispose()
                iterator.remove()
            }
        }
    }
}
