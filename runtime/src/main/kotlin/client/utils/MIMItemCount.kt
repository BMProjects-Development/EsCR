package com.algorithmlx.ecr.client.utils

import net.minecraft.network.chat.Component

data class MIMItemCount(val text: String, val scale: Float) {
    companion object {
        private const val PLAIN_MAX_LENGTH = 2
        private const val ABBREVIATED_MAX_LENGTH = 3
        private const val FULL_SCALE = 1f
        private const val REDUCED_SCALE = 0.5f
        private val UNITS = listOf(
            1000000000L to "screen.escr.mim.count.billion", 1000000L to "screen.escr.mim.count.million",
            1000L to "screen.escr.mim.count.thousand"
        )

        fun format(count: Long, craftable: Boolean = false): MIMItemCount {
            val unit = UNITS.firstOrNull { count >= it.first }
            val text = when {
                count == 0L && craftable -> "+"
                unit != null -> Component.translatable(unit.second, count / unit.first).string
                else -> count.toString()
            }
            val maxLength = if (unit == null) PLAIN_MAX_LENGTH else ABBREVIATED_MAX_LENGTH
            return MIMItemCount(text, if (text.length <= maxLength) FULL_SCALE else REDUCED_SCALE)
        }
    }
}
