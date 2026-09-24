package com.algorithmlx.ecr.client.book

import com.algorithmlx.ecr.init.config.ECConfig
import kotlin.math.roundToInt

object ResearchBookConfigValues {
    fun spaceColor(panX: Float, panY: Float, zoom: Float): Int {
        val space = ECConfig.current.researchBook.space
        val strength = space.parallaxStrength.takeIf(Float::isFinite)?.coerceIn(0F, 2F) ?: 1F
        val encodedX = encodeOffset(panX * strength)
        val encodedY = encodeOffset(panY * strength)
        val encodedZoom = (((zoom - MIN_ZOOM) / (MAX_ZOOM - MIN_ZOOM)).coerceIn(0F, 1F) * ZOOM_MAX).roundToInt()

        val lowY = encodedY and OFFSET_LOW_MASK
        val red = encodedX ushr OFFSET_LOW_BITS
        val green = encodedY ushr OFFSET_LOW_BITS
        val blue = ((lowY ushr 3) shl 7) or encodedZoom
        val alpha = 0x80 or ((encodedX and OFFSET_LOW_MASK) shl 3) or (lowY and 0x7)
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }

    fun zoomStep(): Float =
        ECConfig.current.researchBook.graph.zoomStep.takeIf(Float::isFinite)?.coerceIn(0.01F, 0.5F) ?: 0.12F

    fun availableBlinkSeconds(): Double =
        ECConfig.current.researchBook.graph.availableBlinkSeconds
            .takeIf(Double::isFinite)
            ?.coerceIn(0.5, 30.0)
            ?: 3.0

    fun spaceShaderConfig(): ResearchBookSpaceShaderConfig {
        val space = ECConfig.current.researchBook.space
        val density = quantize(space.starDensity, STAR_DENSITY_MIN, STAR_DENSITY_MAX, STAR_STEP)
        val size = quantize(space.starSize, STAR_SIZE_MIN, STAR_SIZE_MAX, STAR_STEP)
        return ResearchBookSpaceShaderConfig(density, size)
    }

    private fun encodeOffset(value: Float): Int {
        val normalized = value.coerceIn(-PARALLAX_PAN_RANGE, PARALLAX_PAN_RANGE) / PARALLAX_PAN_RANGE
        return ((normalized * 0.5F + 0.5F) * OFFSET_MAX).roundToInt()
    }

    private fun quantize(value: Float, min: Float, max: Float, step: Float): Float {
        val safeValue = value.takeIf(Float::isFinite) ?: 1F
        return ((safeValue.coerceIn(min, max) / step).roundToInt() * step)
    }

    private const val MIN_ZOOM = 0.5F
    private const val MAX_ZOOM = 2F
    private const val PARALLAX_PAN_RANGE = 8192F

    private const val OFFSET_LOW_BITS = 4
    private const val OFFSET_LOW_MASK = (1 shl OFFSET_LOW_BITS) - 1
    private const val OFFSET_MAX = (1 shl 12) - 1
    private const val ZOOM_BITS = 7
    private const val ZOOM_MAX = (1 shl ZOOM_BITS) - 1

    private const val STAR_DENSITY_MIN = 0.25F
    private const val STAR_DENSITY_MAX = 2F
    private const val STAR_SIZE_MIN = 0.5F
    private const val STAR_SIZE_MAX = 3F
    private const val STAR_STEP = 0.05F
}

data class ResearchBookSpaceShaderConfig(
    val starDensity: Float,
    val starSize: Float
) {
    val key: String = "d${(starDensity * 100F).roundToInt()}_s${(starSize * 100F).roundToInt()}"
    val isDefault: Boolean = starDensity == 1F && starSize == 1F
}
