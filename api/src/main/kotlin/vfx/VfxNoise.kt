package com.algorithmlx.ecr.api.vfx

import kotlin.math.floor

internal object VfxNoise {
    fun fractal(x: Float, y: Float, z: Float, seed: Int, octaves: Int): Float {
        var amplitude = 1f
        var frequency = 1f
        var sum = 0f
        var weight = 0f
        repeat(octaves.coerceIn(1, 5)) { octave ->
            sum += sample(x * frequency, y * frequency, z * frequency, seed + octave) * amplitude
            weight += amplitude
            amplitude *= 0.5f
            frequency *= 2f
        }
        return sum / weight
    }

    private fun sample(x: Float, y: Float, z: Float, seed: Int): Float {
        val ix = floor(x).toInt()
        val iy = floor(y).toInt()
        val iz = floor(z).toInt()
        fun fade(t: Float) = t * t * t * (t * (t * 6f - 15f) + 10f)
        fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
        val tx = fade(x - ix)
        val ty = fade(y - iy)
        val tz = fade(z - iz)
        fun row(dy: Int, dz: Int) = mix(lattice(ix, iy + dy, iz + dz, seed), lattice(ix + 1, iy + dy, iz + dz, seed), tx)
        return mix(mix(row(0, 0), row(1, 0), ty), mix(row(0, 1), row(1, 1), ty), tz)
    }

    private fun lattice(x: Int, y: Int, z: Int, seed: Int): Float {
        var hash = seed xor (x * 0x1f123bb5) xor (y * 0x5f356495) xor (z * 0x6c8e9cf5)
        hash = (hash xor (hash ushr 16)) * 0x7feb352d
        hash = (hash xor (hash ushr 15)) * 0x846ca68b.toInt()
        return ((hash xor (hash ushr 16)) ushr 8) / 8388607.5f - 1f
    }
}
