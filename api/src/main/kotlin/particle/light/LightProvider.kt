package com.algorithmlx.ecr.api.particle.light

import org.joml.Vector3f

fun interface LightProvider {
    fun query(pos: Vector3f): Int
}
