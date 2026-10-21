package com.algorithmlx.ecr.api.vfx

internal object VfxColors {
    fun multiply(vararg colors: FloatArray): Int {
        fun channel(index: Int) = (colors.fold(1f) { product, color -> product * color[index] }.coerceIn(0f, 1f) * 255f).toInt()
        return channel(3) shl 24 or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
    }
}
