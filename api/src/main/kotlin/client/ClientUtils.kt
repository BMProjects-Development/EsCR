package com.algorithmlx.ecr.api.client

import com.algorithmlx.ecr.api.mru.storage.MRUStorage
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.network.chat.Component
import java.awt.Color
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt

fun isCursorAtPos(cursorX: Int, cursorY: Int, x: Int, y: Int, width: Int, height: Int) : Boolean =
    isCursorAtPos(cursorX.toDouble(), cursorY.toDouble(), x, y, width, height)

fun isCursorAtPos(cursorX: Double, cursorY: Double, x: Int, y: Int, width: Int, height: Int) : Boolean =
    cursorX >= x && cursorY >= y && cursorX <= x + width && cursorY <= y + height

class MRULineAnimation(
    private val smoothingSpeed: Double = DEFAULT_SMOOTHING_SPEED
) {
    private var displayedFill = 0.0
    private var initialized = false

    init {
        require(smoothingSpeed.isFinite() && smoothingSpeed > 0.0) {
            "MRU line smoothing speed must be finite and positive"
        }
    }

    fun sample(storage: MRUStorage, deltaTicks: Float): Double =
        sample(storage.mru, storage.mruCapacity, deltaTicks)

    internal fun sample(mru: Int, capacity: Int, deltaTicks: Float): Double {
        val targetFill = calculateMRUFill(mru, capacity)
        if (!initialized) {
            initialized = true
            displayedFill = targetFill
            return displayedFill
        }

        val elapsedTicks = if (deltaTicks.isFinite()) deltaTicks.coerceIn(0F, MAX_DELTA_TICKS).toDouble()
        else 0.0
        val blend = 1.0 - exp(-smoothingSpeed * elapsedTicks / TICKS_PER_SECOND)
        displayedFill += (targetFill - displayedFill) * blend
        if (abs(targetFill - displayedFill) < SNAP_THRESHOLD) displayedFill = targetFill
        displayedFill = displayedFill.coerceIn(0.0, 1.0)
        return displayedFill
    }

    companion object {
        private const val DEFAULT_SMOOTHING_SPEED = 3.0
        private const val TICKS_PER_SECOND = 20.0
        private const val MAX_DELTA_TICKS = 5F
        private const val SNAP_THRESHOLD = 0.0001
    }
}

fun drawMRULine(
    graphics: GuiGraphicsExtractor,
    storage: MRUStorage,
    x: Int,
    y: Int,
    xo: Int,
    yo: Int,
    width: Int,
    height: Int,
    mouseX: Int,
    mouseY: Int,
    colorIn: Int = Color(139, 0, 255).rgb,
    colorOut: Int = Color(50, 18, 122).rgb,
    animation: MRULineAnimation? = null,
    deltaTicks: Float = 1F
) {
    drawMRUGradientLine(
        graphics, storage, x, y, xo, yo, height, width, colorIn, colorOut, animation, deltaTicks
    )
    if (isCursorAtPos(mouseX, mouseY, xo + x, yo + y, width, height))
        graphics.setTooltipForNextFrame(
            Component.literal("${storage.mruType.name.string}: ${storage.mru}/${storage.mruCapacity}"),
            mouseX, mouseY
        )
}


fun drawMRUGradientLine(
    gg: GuiGraphicsExtractor,
    container: MRUStorage,
    x: Int,
    y: Int,
    xo: Int,
    yo: Int,
    height: Int,
    width: Int,
    colorIn: Int = Color(139, 0, 255).rgb,
    colorOut: Int = Color(50, 18, 122).rgb,
    animation: MRULineAnimation? = null,
    deltaTicks: Float = 1F
) {
    val fill = animation?.sample(container, deltaTicks) ?: calculateMRUFill(container.mru, container.mruCapacity)
    if (fill <= 0.0 || width <= 0 || height <= 0) return

    val exactWidth = fill.coerceIn(0.0, 1.0) * width
    val wholePixels = floor(exactWidth).toInt().coerceIn(0, width)
    if (wholePixels > 0)
        gg.fillGradient(
            x + xo, y + yo,
            x + xo + wholePixels, y + yo + height,
            colorIn, colorOut
        )

    val fractionalPixel = exactWidth - wholePixels
    if (wholePixels < width && fractionalPixel > 0.0)
        gg.fillGradient(
            x + xo + wholePixels, y + yo,
            x + xo + wholePixels + 1, y + yo + height,
            scaleAlpha(colorIn, fractionalPixel),
            scaleAlpha(colorOut, fractionalPixel)
        )
}

fun calculateMRULineWidth(mru: Int, capacity: Int, width: Int): Int {
    return calculateMRULineWidth(calculateMRUFill(mru, capacity), width)
}

private fun calculateMRUFill(mru: Int, capacity: Int): Double =
    if (mru <= 0 || capacity <= 0) 0.0 else (mru.toDouble() / capacity).coerceIn(0.0, 1.0)

private fun calculateMRULineWidth(fill: Double, width: Int): Int =
    if (width <= 0) 0 else (fill.coerceIn(0.0, 1.0) * width).toInt()

private fun scaleAlpha(color: Int, scale: Double): Int {
    val alpha = (color ushr 24) and 0xFF
    val scaledAlpha = (alpha * scale.coerceIn(0.0, 1.0)).roundToInt().coerceIn(0, 0xFF)
    return (color and 0x00FFFFFF) or (scaledAlpha shl 24)
}
