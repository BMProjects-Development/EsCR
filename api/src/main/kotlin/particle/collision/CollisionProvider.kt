package com.algorithmlx.ecr.api.particle.collision

import org.joml.Vector3f

fun interface CollisionProvider {
    fun query(pos: Vector3f, size: Float, offset: Vector3f): Pair<Vector3f, Vector3f>?
}
