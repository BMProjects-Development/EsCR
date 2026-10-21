package com.algorithmlx.ecr.client.book

import com.algorithmlx.ecr.api.client.research.BookElementRenderContext
import com.algorithmlx.ecr.api.research.content.BookResearchLink
import com.algorithmlx.ecr.api.research.content.BookText
import com.algorithmlx.ecr.api.research.content.TextBookElement
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

object BookLinkedTextLayout {
    private const val LINK_COLOR = 0xFF2F67B1.toInt()
    private const val LINK_HOVER_COLOR = 0xFF1B4F91.toInt()

    private val TOKEN_REGEX = Regex("\n|\\S+|\\s+")

    fun singleLineWidth(text: BookText, font: Font, owner: Identifier?): Int =
        parse(text.resolvedText(), owner).sumOf { font.width(it.text) }

    fun lineCount(text: BookText, font: Font, width: Int, owner: Identifier?): Int =
        layout(text, font, width, owner).size

    fun layout(text: BookText, font: Font, width: Int, owner: Identifier?): List<BookLinkedTextLine> =
        layout(parse(text.resolvedText(), owner), font, width)

    fun render(context: BookElementRenderContext, element: TextBookElement) {
        val font = context.mc.font
        val lines = layout(element.text, font, context.width.coerceAtLeast(1), context.research)
        val start = context.textLineStart.coerceAtLeast(0)
        val count = context.textLineCount.takeIf { it > 0 } ?: lines.size
        val visibleLines = lines.asSequence()
            .drop(start)
            .take(count)
            .take(context.height / font.lineHeight)

        visibleLines.forEachIndexed { index, line ->
            var x = if (element.centered) context.x + (context.width - line.width) / 2 else context.x
            val y = context.y + index * font.lineHeight
            line.segments.forEach { segment ->
                val segmentWidth = font.width(segment.text)
                val hovered = segment.link?.let {
                    BookResearchLinkController.hover(context, it, x, y, segmentWidth, font.lineHeight)
                } == true
                val color = when {
                    segment.link == null -> element.color
                    hovered -> LINK_HOVER_COLOR
                    else -> LINK_COLOR
                }
                context.graphics.text(font, segment.text, x, y, color, element.shadow)
                if (segment.link != null) {
                    val underlineY = y + font.lineHeight - 1
                    context.graphics.fill(x, underlineY, x + segmentWidth, underlineY + 1, color)
                }
                x += segmentWidth
            }
        }
    }

    private fun layout(source: List<BookLinkedTextSegment>, font: Font, width: Int): List<BookLinkedTextLine> {
        val maxWidth = width.coerceAtLeast(1)
        val lines = mutableListOf<BookLinkedTextLine>()
        val segments = mutableListOf<BookLinkedTextSegment>()
        var lineWidth = 0

        fun append(text: String, link: BookResearchLink?) {
            if (text.isEmpty()) return
            segments += BookLinkedTextSegment(text, link)
            lineWidth += font.width(text)
        }

        fun finishLine(force: Boolean = false) {
            while (segments.lastOrNull()?.text?.isBlank() == true) {
                lineWidth -= font.width(segments.removeLast().text)
            }
            val last = segments.lastOrNull()
            if (last != null) {
                val trimmed = last.text.trimEnd()
                if (trimmed != last.text) {
                    lineWidth -= font.width(last.text)
                    segments[segments.lastIndex] = last.copy(text = trimmed)
                    lineWidth += font.width(trimmed)
                }
            }
            if (segments.isNotEmpty() || force) lines += BookLinkedTextLine(segments.toList(), lineWidth)
            segments.clear()
            lineWidth = 0
        }

        fun appendWord(word: String, link: BookResearchLink?) {
            var remaining = word
            while (remaining.isNotEmpty()) {
                val remainingWidth = font.width(remaining)
                if (lineWidth > 0 && lineWidth + remainingWidth > maxWidth) {
                    finishLine()
                    continue
                }
                if (remainingWidth <= maxWidth - lineWidth) {
                    append(remaining, link)
                    return
                }

                var end = 1
                while (end <= remaining.length && font.width(remaining.substring(0, end)) <= maxWidth - lineWidth) {
                    end++
                }
                val split = (end - 1).coerceAtLeast(1)
                append(remaining.substring(0, split), link)
                remaining = remaining.substring(split)
                finishLine()
            }
        }

        source.forEach { segment ->
            TOKEN_REGEX.findAll(segment.text).forEach { match ->
                val token = match.value
                when {
                    token == "\n" -> finishLine(true)
                    token.isBlank() -> {
                        if (lineWidth == 0) return@forEach
                        val tokenWidth = font.width(token)
                        if (lineWidth + tokenWidth > maxWidth) finishLine() else append(token, segment.link)
                    }
                    else -> appendWord(token, segment.link)
                }
            }
        }
        finishLine()
        return lines
    }

    private fun parse(value: String, owner: Identifier?): List<BookLinkedTextSegment> {
        val segments = mutableListOf<BookLinkedTextSegment>()
        var index = 0

        fun add(text: String, link: BookResearchLink? = null) {
            if (text.isNotEmpty()) segments += BookLinkedTextSegment(text, link)
        }

        while (index < value.length) {
            val start = value.indexOf('[', index)
            if (start < 0) {
                add(value.substring(index))
                break
            }

            val labelEnd = value.indexOf("](", start + 1)
            val targetEnd = if (labelEnd >= 0) value.indexOf(')', labelEnd + 2) else -1
            if (labelEnd < 0 || targetEnd < 0) {
                add(value.substring(index))
                break
            }

            add(value.substring(index, start))
            val label = value.substring(start + 1, labelEnd)
            val target = value.substring(labelEnd + 2, targetEnd)
            add(label, BookResearchLink.parse(target, owner))
            index = targetEnd + 1
        }

        return segments
    }

    private fun BookText.resolvedText(): String =
        if (translated) Component.translatable(value).string else value
}

data class BookLinkedTextLine(
    val segments: List<BookLinkedTextSegment>,
    val width: Int
)

data class BookLinkedTextSegment(
    val text: String,
    val link: BookResearchLink?
)
