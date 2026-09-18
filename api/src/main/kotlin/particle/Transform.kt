package com.algorithmlx.ecr.api.particle

import com.algorithmlx.ecr.api.geo.GeoAnimatable
import net.minecraft.world.entity.Entity
import org.joml.Quaternionf
import org.joml.Vector3f

interface Transform {
    val parent: Transform?
    val isValid: Boolean
    val position: Vector3f
    val rotation: Quaternionf
    val velocity: Vector3f

    object Zero : Transform {
        override val parent: Transform? = null
        override val isValid: Boolean = true
        override val position: Vector3f get() = Vector3f()
        override val rotation: Quaternionf get() = Quaternionf()
        override val velocity: Vector3f get() = Vector3f()
    }

    companion object {
        fun create(
            position: Vector3f = Vector3f(),
            rotation: Quaternionf = Quaternionf(),
        ): Transform = object : Transform {
            override val parent: Transform? = null
            override val isValid: Boolean = true
            override val position: Vector3f get() = position
            override val rotation: Quaternionf get() = rotation
            override val velocity: Vector3f get() = Vector3f()
        }

        @JvmStatic
        @JvmOverloads
        fun bone(
            entity: Entity,
            bone: String,
            offset: Vector3f = Vector3f()
        ): Transform = GeoEntityBoneTransform.create(entity, bone, offset)
    }
}
