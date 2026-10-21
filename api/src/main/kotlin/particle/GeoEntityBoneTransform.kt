package com.algorithmlx.ecr.api.particle

import com.algorithmlx.ecr.api.geo.GeoAnimatable
import com.algorithmlx.ecr.api.geo.client.BedrockGeoAnimator
import com.algorithmlx.ecr.api.geo.client.BedrockGeoAssets
import com.algorithmlx.ecr.api.geo.client.BedrockGeoRenderEngine
import com.algorithmlx.ecr.api.geo.client.ClientGeoAnimations
import net.minecraft.world.entity.Entity
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI

class GeoEntityBoneTransform private constructor(
    private val entity: Entity,
    private val animatable: GeoAnimatable,
    val bone: String,
    offset: Vector3f
) : Transform {
    private val localOffset = Vector3f(offset)
    private val resolvedPosition = Vector3f()
    private val resolvedRotation = Quaternionf()
    private val resolvedVelocity = Vector3f()
    private var lastUpdateTick = Long.MIN_VALUE
    private var resolvable = true

    override val parent: Transform? = null

    override val isValid: Boolean
        get() = !entity.isRemoved && resolvable

    override val position: Vector3f
        get() {
            update()
            return Vector3f(resolvedPosition)
        }

    override val rotation: Quaternionf
        get() {
            update()
            return Quaternionf(resolvedRotation)
        }

    override val velocity: Vector3f
        get() {
            update()
            return Vector3f(resolvedVelocity)
        }

    private fun update() {
        val tick = entity.level().gameTime
        if (tick == lastUpdateTick) return
        lastUpdateTick = tick

        val model = runCatching(animatable::geoModel).getOrNull()
        val baked = model?.let(BedrockGeoAssets::get)
        val boneIndex = baked?.boneIndices?.get(bone)
        if (model == null || baked == null || boneIndex == null) {
            resolvable = false
            return
        }

        val now = ClientGeoAnimations.clientTimeSeconds()
        val molang = animatable.geoMolangContext(0F)
        val pose = BedrockGeoAnimator.pose(
            baked,
            ClientGeoAnimations.snapshot(animatable.geoAnimationState, molang, now),
            molang,
            now
        )
        val bakedBone = baked.bones[boneIndex]
        val entityRotation = 180F - entity.yRot
        val transform = Matrix4f()
            .rotateY(radians(entityRotation))
            .scale(model.scale)
            .mul(pose.transforms[boneIndex])
        val bonePosition = Vector3f(
            bakedBone.pivotX,
            bakedBone.pivotY,
            bakedBone.pivotZ
        ).add(localOffset)
        transform.transformPosition(bonePosition)
        resolvedPosition.set(
            entity.x.toFloat() + bonePosition.x,
            entity.y.toFloat() + bonePosition.y,
            entity.z.toFloat() + bonePosition.z
        )
        transform.getUnnormalizedRotation(resolvedRotation).normalize()
        resolvedVelocity.set(
            entity.deltaMovement.x.toFloat(),
            entity.deltaMovement.y.toFloat(),
            entity.deltaMovement.z.toFloat()
        )
        resolvable = true
    }

    companion object {
        @JvmStatic
        @JvmOverloads
        fun create(entity: Entity, bone: String, offset: Vector3f = Vector3f()): GeoEntityBoneTransform {
            require(bone.isNotBlank()) { "GEO bone name must not be blank" }
            val animatable = entity as? GeoAnimatable
                ?: throw IllegalArgumentException("Entity ${entity.type} is not GEO-animatable")
            val model = animatable.geoModel
            val baked = BedrockGeoAssets[model]
                ?: throw IllegalArgumentException("GEO geometry '${model.geometry}' is not loaded")
            require(bone in baked.boneIndices) {
                "GEO geometry '${baked.identifier}' has no bone '$bone'"
            }
            return GeoEntityBoneTransform(entity, animatable, bone, offset)
        }

        private fun radians(degrees: Float): Float = degrees * (PI.toFloat() / 180F)
    }
}
