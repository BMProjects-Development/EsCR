package com.algorithmlx.ecr.api.vfx

import org.joml.Vector3f

internal class VfxCameraShake(spec: VfxNode) {
    val strength = spec.properties["strength"].value(1f)
    val frequency = spec.properties["frequency"].value(14f)
    val amplitude = if (spec.properties["amplitude"] == null) Vector3f(1.5f, 1.5f, 0.75f) else spec.properties.vector("amplitude")
}
