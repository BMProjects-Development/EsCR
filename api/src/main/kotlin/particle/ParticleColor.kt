package com.algorithmlx.ecr.api.particle

data class ParticleColor(
    val r: Float,
    val g: Float,
    val b: Float,
    val a: Float = 1f
) {
    fun mix(other: ParticleColor, weight: Float) = ParticleColor(
        r + (other.r - r) * weight,
        g + (other.g - g) * weight,
        b + (other.b - b) * weight,
        a + (other.a - a) * weight
    )

    companion object {
        val WHITE = ParticleColor(1f, 1f, 1f, 1f)
    }
}
