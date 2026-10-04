package com.algorithmlx.ecr.client.book

import com.algorithmlx.ecr.api.research.ClientResearchState
import com.algorithmlx.ecr.api.research.content.BookElementAlign
import com.algorithmlx.ecr.api.research.content.BookElementSerializer
import com.algorithmlx.ecr.api.research.content.BookElementSpec
import com.algorithmlx.ecr.api.research.content.BookEntry
import com.algorithmlx.ecr.api.research.content.BookTextVariant
import com.algorithmlx.ecr.api.research.content.CraftingBookElement
import com.algorithmlx.ecr.api.research.content.GroupBookElement
import com.algorithmlx.ecr.api.research.content.SpaceBookElement
import com.algorithmlx.ecr.api.research.content.TaskListBookElement
import com.algorithmlx.ecr.api.research.content.TextBookElement
import com.algorithmlx.ecr.api.research.serializer.ResearchSerializers
import com.algorithmlx.ecr.client.book.renderer.BookRecipeElementRenderer
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import net.minecraft.util.FormattedCharSequence
import kotlin.math.ceil

data class BookElementPlacement(
    val element: BookElementSpec,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val textLines: List<FormattedCharSequence>? = null,
    val textLineStart: Int = 0,
    val textLineCount: Int = textLines?.size ?: 0
)

data class BookSpread(
    val elements: List<BookElementPlacement>
)

object BookPageLayout {
    private const val FIRST_X = 16
    private const val SECOND_X = 271
    private const val TOP = 26
    private const val BOTTOM = 230
    private const val PAGE_WIDTH = 225
    private const val PAGE_HEIGHT = BOTTOM - TOP

    fun paginate(entry: BookEntry): List<BookSpread> {
        val spreads = mutableListOf<MutableList<BookElementPlacement>>(mutableListOf())
        val cursor = PageCursor(spreads)

        taskElement(entry)?.let { spec ->
            val height = spec.height ?: TASK_CELL_SIZE
            spreads.last() += BookElementPlacement(spec, cursor.alignedX(PAGE_WIDTH), cursor.y, PAGE_WIDTH, height)
            cursor.y += height
        }

        entry.pages.forEachIndexed { pageIndex, page ->
            if (pageIndex > 0) cursor.nextSide()

            page.elements.forEach { originalSpec ->
                val spec = originalSpec.resolveText(entry) ?: return@forEach
                placeElement(spec, cursor, entry)
            }
        }

        return spreads.map(::BookSpread).ifEmpty { listOf(BookSpread(emptyList())) }
    }

    private fun placeElement(
        spec: BookElementSpec,
        cursor: PageCursor,
        entry: BookEntry,
    ) {
        val serializer = ResearchSerializers.elementSerializer(spec.content.type)

        val width = (spec.width ?: autoWidth(spec, entry) ?: serializer?.defaultWidth ?: 16).coerceIn(0, PAGE_WIDTH)

        if (spec.content is TextBookElement) {
            placeText(spec, width.coerceAtLeast(1), cursor, entry.id)
            return
        }

        val measuredHeight = autoHeight(spec, width, entry)
        val height = elementHeight(
            spec,
            measuredHeight,
            serializer?.defaultHeight ?: 16
        ).coerceIn(0, PAGE_HEIGHT)

        if (spec.content === SpaceBookElement) {
            cursor.advance(height)
            return
        }

        if (cursor.remainingHeight < height) cursor.nextSide()

        cursor.place(spec, width, height)
    }

    private fun taskElement(entry: BookEntry): BookElementSpec? {
        if (entry.taskLevels.isEmpty()) return null
        val level = if (ClientResearchState.has(entry.id)) entry.taskLevels.lastIndex
        else ClientResearchState.completedTaskLevels(entry.id).coerceIn(0, entry.taskLevels.lastIndex)

        val visibleTasks = entry.taskLevels[level].tasks.count { !it.hidden }
        if (visibleTasks == 0) return null
        val rows = ceil(visibleTasks / TASKS_PER_ROW.toFloat()).toInt().coerceAtLeast(1)
        return BookElementSpec(TaskListBookElement(entry.id, level), PAGE_WIDTH, rows * TASK_CELL_SIZE + 4)
    }

    private fun autoWidth(
        spec: BookElementSpec,
        entry: BookEntry
    ): Int? = when (val element = spec.content) {
        is TextBookElement -> BookLinkedTextLayout.singleLineWidth(
            element.text,
            Minecraft.getInstance().font,
            entry.id
        ).coerceIn(1, PAGE_WIDTH)
        is CraftingBookElement -> BookRecipeElementRenderer.preferredWidth(element)
        else -> null
    }

    private fun autoHeight(
        spec: BookElementSpec,
        width: Int,
        entry: BookEntry
    ): Int? =
        when (val element = spec.content) {
            is CraftingBookElement -> BookRecipeElementRenderer.preferredHeight(element, width, entry.id)
            is GroupBookElement -> BookGroupLayout.layout(element, width, entry.id).height
            else -> null
        }

    private fun elementHeight(
        spec: BookElementSpec,
        measuredHeight: Int?,
        defaultHeight: Int
    ): Int {
        if (spec.content is CraftingBookElement && measuredHeight != null) {
            return maxOf(spec.height ?: 0, measuredHeight)
        }
        return spec.height ?: measuredHeight ?: defaultHeight
    }

    private fun placeText(
        spec: BookElementSpec,
        width: Int,
        cursor: PageCursor,
        owner: Identifier
    ) {
        val font = Minecraft.getInstance().font
        val element = spec.content as TextBookElement
        val linkedLines = BookLinkedTextLayout.layout(element.text, font, width, owner)
        if (linkedLines.isEmpty()) return
        var line = 0
        while (line < linkedLines.size) {
            val availableLines = (BOTTOM - cursor.y) / font.lineHeight
            if (availableLines == 0) {
                cursor.nextSide()
                continue
            }
            val lineCount = minOf(availableLines, linkedLines.size - line)
            val height = lineCount * font.lineHeight
            cursor.spreads.last() += BookElementPlacement(
                spec,
                cursor.alignedX(width, spec.align),
                cursor.y,
                width,
                height,
                textLineStart = line,
                textLineCount = lineCount
            )
            line += lineCount
            cursor.y += height
            if (line < linkedLines.size) {
                cursor.nextSide()
            }
        }
    }

    private fun BookElementSpec.resolveText(entry: BookEntry): BookElementSpec? {
        val element = content as? TextBookElement ?: return this
        val candidates = buildList {
            add(BookTextVariant(element.text, element.requirement))
            addAll(element.variants)
        }
        val selected = candidates.lastOrNull { variant ->
            variant.requirement?.let { ClientResearchState.requirementMet(entry.id, it) } == true
        }
            ?: candidates.lastOrNull { it.requirement == null }
            ?: return null
        return copy(content = element.copy(text = selected.text, requirement = null, variants = emptyList()))
    }

    private class PageCursor(val spreads: MutableList<MutableList<BookElementPlacement>>) {
        var side = 0
        var y = TOP
        val x get() = if (side % 2 == 0) FIRST_X else SECOND_X
        val remainingHeight: Int get() = BOTTOM - y

        fun alignedX(width: Int, align: BookElementAlign = BookElementAlign.LEFT): Int = when (align) {
            BookElementAlign.LEFT -> x
            BookElementAlign.CENTER -> x + (PAGE_WIDTH - width) / 2
            BookElementAlign.RIGHT -> x + PAGE_WIDTH - width
        }

        fun place(spec: BookElementSpec, width: Int, height: Int) {
            spreads.last() += BookElementPlacement(spec, alignedX(width, spec.align), y, width, height)
            y += height
        }

        fun advance(height: Int) {
            y = (y + height).coerceAtMost(BOTTOM)
        }

        fun nextSide() {
            side++
            y = TOP
            if (side % 2 == 0) spreads.add(mutableListOf())
        }
    }

    private const val TASK_CELL_SIZE = 20
    private const val TASKS_PER_ROW = PAGE_WIDTH / TASK_CELL_SIZE
}
