package com.algorithmlx.ecr.api.particle

import com.algorithmlx.ecr.api.molang.compiler.FloatExpr
import com.algorithmlx.ecr.api.molang.compiler.eval
import com.algorithmlx.ecr.api.molang.runtime.LivingEntityQuery
import com.algorithmlx.ecr.api.molang.runtime.MolangContext
import com.algorithmlx.ecr.api.molang.runtime.Variables
import com.algorithmlx.ecr.api.molang.runtime.VariablesMap
import com.algorithmlx.ecr.api.particle.file.ParticleComponents
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.util.LightCoordsUtil
import org.joml.Quaternionf
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector3fc
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

class BedrockParticle(
    val emitter: ParticleEmitter,
    private val localSpace: Transform?
) {
    private val components = emitter.effect.components
    private val curveVariables: CurveVariables = CurveVariables({ molang }, emitter.effect.curves)
    private val variables: Variables = VariablesMap()
        .fallbackBackTo(curveVariables)
        .fallbackBackTo(emitter.context.variables)
    private val molang: MolangContext = MolangContext(emitter.sourceEntity, variables)

    private var firedCreationEvents = false
    private var firedExpirationEvents = false
    private var nextTimelineEvent: Map.Entry<Float, List<String>>? = null
    private var age: Float by variables.getOrPut("particle_age", 0f)
    private var lifetime: Float by variables.getOrPut("particle_lifetime", 0f)

    val position = Vector3f()
    val velocity = Vector3f()
    val direction = Vector3f()
    val globalPosition: Vector3f
        get() = if (localSpace == null) Vector3f(position) else Vector3f(position).rotate(localSpace.rotation).add(localSpace.position)
    val globalVelocity: Vector3f
        get() = if (localSpace == null) Vector3f(velocity) else Vector3f(velocity).rotate(localSpace.rotation)

    private val emitterRotationOnEmit = Quaternionf(emitter.rotation)
    private val emitterPositionOnEmit = Vector3f(emitter.position)
    private var rotationAngle = components.particleInitialSpin?.rotation?.eval(molang) ?: 0f
    private var rotationRate = components.particleInitialSpin?.rotationRate?.eval(molang) ?: 0f
    private val previousPosition = Vector3f()
    private var previousRotationAngle = rotationAngle
    private var interpolationInitialized = false

    init {
        for (i in 1..4) variables["particle_random_$i"] = emitter.system.random.nextFloat()
        age = 0f
        lifetime = components.particleLifetimeExpression?.maxLifetime?.eval(molang) ?: 0f
        nextTimelineEvent = components.particleLifetimeEvents.timeline.lowestEntry()
    }

    fun emit(inheritVelocity: Boolean) {
        var emittedPosition = Vector3f()
        var emittedDirection = Vector3f()
        val random = emitter.system.random

        fun ParticleComponents.Direction.computeFor(point: Vector3fc): Vector3f = when (this) {
            ParticleComponents.Direction.Inwards -> Vector3f(point).negate()
            ParticleComponents.Direction.Outwards -> Vector3f(point)
            is ParticleComponents.Direction.Custom -> vec.eval(molang)
        }

        components.emitterShapePoint?.let { config ->
            emittedPosition = config.offset.eval(molang)
            val point = createShape()
            safeNormalize(point)
            emittedDirection = config.direction.computeFor(point)
        }

        components.emitterShapeBox?.let { config ->
            val point = Vector3f(
                random.nextFloat() * 2f - 1f,
                random.nextFloat() * 2f - 1f,
                random.nextFloat() * 2f - 1f
            )
            if (config.surfaceOnly) {
                val side = random.nextInt(6)
                val value = if (side >= 3) 1f else -1f
                when (side % 3) {
                    0 -> point.x = value
                    1 -> point.y = value
                    else -> point.z = value
                }
            }
            point.mul(config.halfDimensions.eval(molang))
            emittedPosition = config.offset.eval(molang).add(point)
            emittedDirection = config.direction.computeFor(point)
        }

        components.emitterShapeSphere?.let { config ->
            val point = createShape()
            if (config.surfaceOnly) safeNormalize(point)
            point.mul(config.radius.eval(molang))
            emittedPosition = config.offset.eval(molang).add(point)
            emittedDirection = config.direction.computeFor(point)
        }

        components.emitterShapeDisc?.let { config ->
            val radius = config.radius.eval(molang)
            val normal = safeNormalize(config.planeNormal.eval(molang))
            var point = Vector3f(1f, 0f, 0f)
            if (abs(point.dot(normal)) > 0.9f) point.set(0f, 1f, 0f)
            point = point.cross(normal).normalize()
            point.rotateAxis(random.nextFloat() * 2f * PI.toFloat(), normal.x, normal.y, normal.z)
            point.mul(radius * if (config.surfaceOnly) 1f else sqrt(random.nextFloat()))
            emittedPosition = config.offset.eval(molang).add(point)
            emittedDirection = config.direction.computeFor(point)
        }

        if (components.emitterLocalSpace?.rotation != true) {
            emittedPosition.rotate(emitter.rotation)
            emittedDirection.rotate(emitter.rotation)
        }
        if (localSpace == null) {
            emittedPosition.add(emitter.position)
        } else {
            emitter.offset?.let(emittedPosition::add)
        }

        position.set(emittedPosition)
        direction.set(emittedDirection)
        safeNormalize(direction)
        velocity.set(direction).mul(components.particleInitialSpeed.eval(molang))
        if (inheritVelocity || components.emitterLocalSpace?.velocity == true) velocity.add(emitter.velocity)
    }

    private fun createShape(): Vector3f {
        val random = emitter.system.random
        val point = Vector3f()
        do {
            point.set(random.nextFloat() * 2f - 1f, random.nextFloat() * 2f - 1f, random.nextFloat() * 2f - 1f)
        } while (point.lengthSquared().let { it > 1f || it == 0f })
        return point
    }

    fun update(dt: Float): Boolean {
        if (interpolationInitialized) {
            previousPosition.set(position)
            previousRotationAngle = rotationAngle
        }

        if (!firedCreationEvents) {
            firedCreationEvents = true
            emitter.fire(dt, components.particleLifetimeEvents.creationEvents, this)
        }
        val alive = updateParticle(dt.coerceAtLeast(0f))
        if (!alive && !firedExpirationEvents) {
            firedExpirationEvents = true
            emitter.fire(0f, components.particleLifetimeEvents.expirationEvents, this)
        }

        if (!interpolationInitialized) {
            previousPosition.set(position)
            previousRotationAngle = rotationAngle
            interpolationInitialized = true
        }
        return alive
    }

    private fun updateParticle(dt: Float): Boolean {
        age += dt
        curveVariables.update()
        fireTimelineEvents()
        if (age >= lifetime) return false

        components.particleLifetimeExpression?.let {
            if (it.expirationExpression.eval(molang) != 0f) return false
        }

        components.particleMotionParametric?.let {
            position.set(it.relativePosition.eval(molang))
            if (localSpace == null) position.add(emitterPositionOnEmit)
            rotationAngle = it.rotation.eval(molang)
            it.direction?.let { expression ->
                direction.set(expression.eval(molang))
                velocity.zero()
            }
        }

        components.particleMotionDynamic?.let {
            val acceleration = it.linearAcceleration.eval(molang)
                .add(Vector3f(velocity).mul(-it.linearDragCoefficient.eval(molang)))
            if (!move(dt, acceleration)) return false

            var rotationalAcceleration = it.rotationAcceleration.eval(molang)
            rotationalAcceleration -= rotationRate * it.rotationDragCoefficient.eval(molang)
            rotationalAcceleration *= dt
            var deltaRotation = rotationRate
            rotationRate += rotationalAcceleration
            deltaRotation += rotationRate
            rotationAngle += deltaRotation * 0.5f * dt
        }

        val billboardDirection = components.particleAppearanceBillboard?.direction
        if (billboardDirection is ParticleComponents.ParticleBillboard.Direction.FromVelocity &&
            velocity.lengthSquared() > billboardDirection.minSpeedThresholdSqr
        ) {
            direction.set(velocity)
        }
        return true
    }

    private fun fireTimelineEvents() {
        while (true) {
            val entry = nextTimelineEvent ?: return
            val timeSinceEvent = age - entry.key
            if (timeSinceEvent < 0f) return
            emitter.fire(timeSinceEvent, entry.value, this)
            nextTimelineEvent = components.particleLifetimeEvents.timeline.higherEntry(entry.key)
        }
    }

    private fun move(dt: Float, acceleration: Vector3f, iteration: Int = 0, sliding: Boolean = false): Boolean {
        val offset = Vector3f(velocity).add(Vector3f(acceleration).mul(0.5f * dt)).mul(dt)
        val collisionConfig = components.particleMotionCollision
        if (collisionConfig == null || collisionConfig.enabled.eval(molang) == 0f) {
            position.add(offset)
            velocity.fma(dt, acceleration)
            return true
        }

        val collision = emitter.system.collisionProvider.query(position, collisionConfig.collisionRadius, offset)
        if (collision == null) {
            position.add(offset)
            velocity.fma(dt, acceleration)
            if (sliding) {
                val originalSpeed = speed(velocity)
                val modifiedSpeed = (originalSpeed - collisionConfig.collisionDrag * dt).coerceAtLeast(0f)
                if (originalSpeed > 1.0e-6f && modifiedSpeed > 1.0e-4f) velocity.mul(modifiedSpeed / originalSpeed)
                else velocity.zero()
            }
            return true
        }

        val (maxOffset, surfaceNormal) = collision
        if (iteration >= 3 || collisionConfig.expireOnContact) {
            position.add(maxOffset)
            velocity.fma(dt, acceleration)
            return !collisionConfig.expireOnContact
        }

        val offsetLengthSquared = offset.lengthSquared()
        val preDt = if (offsetLengthSquared > 0f) {
            sqrt(maxOffset.lengthSquared() / offsetLengthSquared).coerceIn(0f, 1f) * dt
        } else 0f
        val velocityBeforeHit = Vector3f(velocity).fma(preDt, acceleration)
        val velocityAfterHit = reflect(velocityBeforeHit, surfaceNormal)
        velocityAfterHit.add(
            Vector3f(surfaceNormal).mul((collisionConfig.coefficientOfRestitution - 1f) * velocityAfterHit.dot(surfaceNormal))
        )
        val positionAtHit = Vector3f(position).add(maxOffset)
        position.set(positionAtHit)
        velocity.set(velocityAfterHit)

        val postDt = dt - preDt
        if (collisionConfig.events.isNotEmpty()) {
            val impactSpeed = (-velocityBeforeHit.dot(surfaceNormal)).coerceAtLeast(0f)
            collisionConfig.events.forEach {
                if (impactSpeed >= it.minSpeed) emitter.fire(postDt, listOf(it.event), this)
            }
        }

        val postOffset = Vector3f(velocityAfterHit).add(Vector3f(acceleration).mul(postDt * 0.5f)).mul(postDt)
        if (Vector3f(positionAtHit).add(postOffset).dot(surfaceNormal) > positionAtHit.dot(surfaceNormal)) {
            return move(postDt, acceleration, iteration + 1, sliding)
        }
        val accelerationInPlane = Vector3f(acceleration)
            .add(Vector3f(surfaceNormal).mul(-acceleration.dot(surfaceNormal)))
        return move(postDt, accelerationInPlane, iteration + 1, true)
    }

    internal fun interpolatedGlobalPosition(partialTick: Float): Vector3f {
        val interpolatedPosition = Vector3f(previousPosition).lerp(position, partialTick)
        return if (localSpace == null) interpolatedPosition
        else interpolatedPosition.rotate(localSpace.rotation).add(localSpace.position)
    }

    internal fun extractBillboard(
        worldPosition: Vector3f,
        partialTick: Float,
        cameraPosition: Vector3fc,
        cameraRotation: Quaternionf,
        cameraFacing: Vector3fc,
        cameraUuid: UUID?,
        firstPerson: Boolean
    ): ParticleQuad? {
        if (cameraUuid == (emitter.sourceEntity as? LivingEntityQuery)?.entity?.uuid) {
            val visibility = components.particleVisibility
            if (!(if (firstPerson) visibility.firstPerson else visibility.thirdPerson)) return null
        }
        val appearance = components.particleAppearanceBillboard ?: return null
        components.particleInitialization?.perRenderExpression?.eval(molang)

        val renderRotationAngle = lerpDegrees(previousRotationAngle, rotationAngle, partialTick)
        val rotation = billboardRotation(appearance, worldPosition, cameraPosition, cameraRotation, renderRotationAngle)
        val size = appearance.size.eval(molang)
        val textureSize = Vector2f(appearance.uv.textureWidth.toFloat(), appearance.uv.textureHeight.toFloat())
        var minUv: Vector2f
        var maxUv: Vector2f

        appearance.uv.flipbook?.let { flipbook ->
            val base = flipbook.base.eval(molang)
            val frameSize = Vector2f(flipbook.size.first, flipbook.size.second)
            val step = Vector2f(flipbook.step.first, flipbook.step.second)
            val maxFrame = flipbook.maxFrame.eval(molang).toInt().coerceAtLeast(1)
            val timePerFrame = if (flipbook.stretchToLifetime) {
                if (lifetime > 0f) lifetime / maxFrame else Float.POSITIVE_INFINITY
            } else if (flipbook.framePerSecond > 0f) {
                1f / flipbook.framePerSecond
            } else Float.POSITIVE_INFINITY
            val rawFrame = if (timePerFrame.isFinite()) (age / timePerFrame).toInt() else 0
            val frame = if (flipbook.loop) rawFrame.mod(maxFrame) else rawFrame.coerceAtMost(maxFrame - 1)
            minUv = base.add(step.mul(frame.toFloat()))
            maxUv = Vector2f(minUv).add(frameSize)
        } ?: run {
            minUv = appearance.uv.uv?.eval(molang) ?: Vector2f()
            maxUv = Vector2f(minUv).add(appearance.uv.uvSize?.eval(molang) ?: textureSize)
        }
        if (textureSize.x != 0f && textureSize.y != 0f) {
            minUv.div(textureSize)
            maxUv.div(textureSize)
        }

        val color = components.particleAppearanceTinting?.color?.eval(molang) ?: ParticleColor.WHITE
        val light = if (components.particleAppearanceLighting != null) {
            emitter.system.lightProvider.query(worldPosition)
        } else LightCoordsUtil.FULL_BRIGHT
        val flip = !emitter.effect.material.backfaceCulling &&
            cameraFacing.dot(Vector3f(0f, 0f, -1f).rotate(rotation)) > 0f
        val distance = Vector3f(cameraPosition).sub(worldPosition)
            .dot(Vector3f(0f, 0f, -1f).rotate(rotation))

        return ParticleQuad(
            position = worldPosition,
            rotation = rotation,
            size = size,
            minUv = minUv,
            maxUv = maxUv,
            color = packColor(color),
            light = light,
            flip = flip,
            distance = distance
        )
    }

    private fun billboardRotation(
        appearance: ParticleComponents.ParticleBillboard,
        worldPosition: Vector3fc,
        cameraPosition: Vector3fc,
        cameraRotation: Quaternionf,
        renderRotationAngle: Float
    ): Quaternionf {
        fun computedDirection(): Vector3f {
            val localDirection = when (val value = appearance.direction) {
                is ParticleComponents.ParticleBillboard.Direction.FromVelocity -> Vector3f(direction)
                is ParticleComponents.ParticleBillboard.Direction.Custom -> value.direction.eval(molang)
            }
            return if (localSpace == null) localDirection else localDirection.rotate(localSpace.rotation)
        }

        val up = Vector3f(0f, 1f, 0f)
        val localRotation = localSpace?.rotation ?: Quaternionf()
        val result = when (appearance.facingCameraMode) {
            ParticleComponents.ParticleBillboard.FacingCameraMode.ROTATE_XYZ -> cameraRotation.opposite()
            ParticleComponents.ParticleBillboard.FacingCameraMode.ROTATE_Y -> cameraRotation.opposite().projectAroundAxis(up)
            ParticleComponents.ParticleBillboard.FacingCameraMode.LOOK_AT_XYZ ->
                lookAt(Vector3f(cameraPosition).sub(worldPosition), up)
            ParticleComponents.ParticleBillboard.FacingCameraMode.LOOK_AT_Y -> {
                val look = Vector3f(cameraPosition).sub(worldPosition).apply { y = 0f }
                lookAt(look, up)
            }
            ParticleComponents.ParticleBillboard.FacingCameraMode.LOOK_AT_DIRECTION -> {
                val particleDirection = safeNormalize(computedDirection())
                val target = Vector3f(cameraPosition).sub(worldPosition)
                target.add(Vector3f(particleDirection).mul(-target.dot(particleDirection)))
                lookAt(target, Vector3f(particleDirection).cross(target))
            }
            ParticleComponents.ParticleBillboard.FacingCameraMode.DIRECTION_X ->
                lookAt(computedDirection(), Vector3f(up).rotate(localRotation))
                    .mul(Quaternionf().rotationY(-PI.toFloat() / 2f))
            ParticleComponents.ParticleBillboard.FacingCameraMode.DIRECTION_Y ->
                lookAt(computedDirection(), Vector3f(up).rotate(localRotation))
                    .mul(Quaternionf().rotationX(-PI.toFloat() / 2f))
                    .mul(Quaternionf().rotationY(PI.toFloat()))
            ParticleComponents.ParticleBillboard.FacingCameraMode.DIRECTION_Z ->
                lookAt(computedDirection(), Vector3f(up).rotate(localRotation))
            ParticleComponents.ParticleBillboard.FacingCameraMode.EMITTER_TRANSFORM_XY ->
                Quaternionf(localSpace?.rotation ?: emitterRotationOnEmit).mul(Quaternionf().rotationY(PI.toFloat()))
            ParticleComponents.ParticleBillboard.FacingCameraMode.EMITTER_TRANSFORM_XZ ->
                Quaternionf(localSpace?.rotation ?: emitterRotationOnEmit)
                    .mul(Quaternionf().rotationY(PI.toFloat()))
                    .mul(Quaternionf().rotationX(PI.toFloat() / 2f))
            ParticleComponents.ParticleBillboard.FacingCameraMode.EMITTER_TRANSFORM_YZ ->
                Quaternionf(localSpace?.rotation ?: emitterRotationOnEmit).mul(Quaternionf().rotationY(-PI.toFloat() / 2f))
        }
        if (renderRotationAngle != 0f) {
            result.mul(Quaternionf().rotationZ(Math.toRadians(-renderRotationAngle.toDouble()).toFloat()))
        }
        return result
    }

    private fun lerpDegrees(start: Float, end: Float, progress: Float): Float {
        val delta = ((end - start + 180f) % 360f + 360f) % 360f - 180f
        return start + delta * progress
    }
}

internal data class ParticleQuad(
    val position: Vector3f,
    val rotation: Quaternionf,
    val size: Vector2f,
    val minUv: Vector2f,
    val maxUv: Vector2f,
    val color: Int,
    val light: Int,
    val flip: Boolean,
    val distance: Float
) {
    fun render(pose: PoseStack.Pose, consumer: VertexConsumer) {
        fun vertex(x: Float, y: Float, u: Float, v: Float) {
            val point = Vector3f(x, y, 0f).rotate(rotation).add(position)
            consumer.addVertex(pose, point)
                .setUv(u, v)
                .setColor(color)
                .setLight(light)
        }
        if (!flip) {
            vertex(-size.x, -size.y, maxUv.x, maxUv.y)
            vertex(-size.x, size.y, maxUv.x, minUv.y)
            vertex(size.x, size.y, minUv.x, minUv.y)
            vertex(size.x, -size.y, minUv.x, maxUv.y)
        } else {
            vertex(size.x, -size.y, minUv.x, maxUv.y)
            vertex(size.x, size.y, minUv.x, minUv.y)
            vertex(-size.x, size.y, maxUv.x, minUv.y)
            vertex(-size.x, -size.y, maxUv.x, maxUv.y)
        }
    }
}

private fun Pair<FloatExpr, FloatExpr>.eval(context: MolangContext) =
    Vector2f(first.eval(context), second.eval(context))
