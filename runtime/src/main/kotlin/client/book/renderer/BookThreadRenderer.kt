package com.algorithmlx.ecr.client.book.renderer

import com.algorithmlx.ecr.client.book.BookRenderPipelines
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Mth
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

object BookThreadRenderer {
    fun render(
        graphics: GuiGraphicsExtractor,
        from: Pair<Int, Int>,
        to: Pair<Int, Int>,
        completed: Boolean,
        baseColor: Int? = null
    ) {
        val dx = (to.first - from.first).toFloat()
        val dy = (to.second - from.second).toFloat()
        val distance = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (distance < 1f) return
        val normalX = -dy / distance
        val normalY = dx / distance
        val strands = if (completed) 1 else 3
        val steps = (distance / if (completed) 1.5f else 2.25f).roundToInt().coerceAtLeast(1)
        val time = System.nanoTime() / 1000000000f
        repeat(strands) { strand ->
            for (step in 0..steps) {
                if (!completed && (step + (time * 10f).toInt() + strand * 3) % 10 >= 6) continue
                val progress = step.toFloat() / steps
                val envelope = sin(Math.PI.toFloat() * progress)
                val wave =
                    if (completed) {
                        sin(progress * Math.PI.toFloat() * 2f + time * 1.4f) * 0.35f * envelope
                    } else {
                        val spread = (strand - 1) * 1.35f
                        val frequency = Math.PI.toFloat() * (4.5f + strand)
                        (
                            spread + sin(progress * frequency + time * (1.6f + strand * 0.18f) + strand * 2.1f) *
                                (2.1f + strand * 0.45f)
                        ) * envelope
                    }
                val x = Mth.lerp(progress, from.first.toFloat(), to.first.toFloat()) + normalX * wave
                val y = Mth.lerp(progress, from.second.toFloat(), to.second.toFloat()) + normalY * wave
                val color =
                    if (baseColor == null) {
                        if (completed) DEFAULT_COMPLETED else DEFAULT_UNFINISHED[strand]
                    } else {
                        threadColor(baseColor, strand, progress, completed)
                    }
                graphics.fill(BookRenderPipelines.THREAD, x.toInt(), y.toInt(), x.toInt() + 2, y.toInt() + 2, color)
            }
        }
    }

    private fun threadColor(
        baseColor: Int,
        strand: Int,
        progress: Float,
        completed: Boolean
    ): Int {
        val alpha = if (baseColor ushr 24 == 0) 0xFF else baseColor ushr 24
        val red = (baseColor ushr 16) and 0xFF
        val green = (baseColor ushr 8) and 0xFF
        val blue = baseColor and 0xFF
        val wave = if (completed) progress else (progress + strand * 0.22f).let { it - it.toInt() }
        val factor = if (completed) 1.18f + wave * 0.18f else 0.72f + wave * 0.32f + strand * 0.08f
        return (alpha.coerceIn(0, 255) shl 24) or
            ((red * factor).toInt().coerceIn(0, 255) shl 16) or
            ((green * factor).toInt().coerceIn(0, 255) shl 8) or
            (blue * factor).toInt().coerceIn(0, 255)
    }

    private const val DEFAULT_COMPLETED = 0xFFEAF8FF.toInt()
    private val DEFAULT_UNFINISHED = intArrayOf(0xFF7184A4.toInt(), 0xFF8B78A8.toInt(), 0xFF657B96.toInt())
}
