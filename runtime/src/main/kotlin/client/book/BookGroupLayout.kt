package com.algorithmlx.ecr.client.book

import com.algorithmlx.ecr.api.research.ClientResearchState
import com.algorithmlx.ecr.api.research.content.BookElementSpec
import com.algorithmlx.ecr.api.research.content.BookTextVariant
import com.algorithmlx.ecr.api.research.content.CraftingBookElement
import com.algorithmlx.ecr.api.research.content.GroupBookElement
import com.algorithmlx.ecr.api.research.content.SpaceBookElement
import com.algorithmlx.ecr.api.research.content.TextBookElement
import com.algorithmlx.ecr.api.research.serializer.ResearchSerializers
import com.algorithmlx.ecr.client.book.renderer.BookRecipeElementRenderer
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

data class BookGroupPlacement(
    val element: BookElementSpec,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class BookGroupLayoutResult(
    val elements: List<BookGroupPlacement>,
    val height: Int
)

object BookGroupLayout {
    fun layout(
        group: GroupBookElement,
        width: Int,
        owner: Identifier?
    ): BookGroupLayoutResult {
        val availableWidth = width.coerceAtLeast(0)
        val placements = mutableListOf<BookGroupPlacement>()
        var x = 0
        var y = 0
        var rowHeight = 0

        fun finishRow() {
            y += rowHeight
            x = 0
            rowHeight = 0
        }

        group.elements.forEach { original ->
            val spec = original.resolveText(owner) ?: return@forEach
            if (spec.content === SpaceBookElement) {
                if (x > 0 || rowHeight > 0) finishRow()
                val serializer = ResearchSerializers.elementSerializer(spec.content.type)
                y += (spec.height ?: serializer?.defaultHeight ?: 8).coerceAtLeast(0)
                return@forEach
            }

            val size = measure(spec, availableWidth, owner)
            if (x > 0 && x + size.width > availableWidth) finishRow()
            placements += BookGroupPlacement(spec, x, y, size.width, size.height)
            x += size.width
            rowHeight = maxOf(rowHeight, size.height)
        }

        if (x > 0 || rowHeight > 0) finishRow()
        return BookGroupLayoutResult(placements, y)
    }

    private fun measure(
        spec: BookElementSpec,
        availableWidth: Int,
        owner: Identifier?
    ): ElementSize {
        val serializer = ResearchSerializers.elementSerializer(spec.content.type)
        val width =
            (
                spec.width
                    ?: autoWidth(spec, availableWidth, owner)
                    ?: serializer?.defaultWidth
                    ?: 16
            ).coerceIn(0, availableWidth)
        val measuredHeight = autoHeight(spec, width, owner)
        val height = when {
            spec.content is CraftingBookElement && measuredHeight != null -> maxOf(spec.height ?: 0, measuredHeight)
            else -> spec.height ?: measuredHeight ?: serializer?.defaultHeight ?: 16
        }.coerceAtLeast(0)
        return ElementSize(width, height)
    }

    private fun autoWidth(
        spec: BookElementSpec,
        availableWidth: Int,
        owner: Identifier?
    ): Int? = when (val element = spec.content) {
        is TextBookElement ->
            BookLinkedTextLayout
                .singleLineWidth(element.text, Minecraft.getInstance().font, owner)
                .coerceIn(1, availableWidth.coerceAtLeast(1))
        is CraftingBookElement -> BookRecipeElementRenderer.preferredWidth(element)
        is GroupBookElement -> availableWidth
        else -> null
    }

    private fun autoHeight(
        spec: BookElementSpec,
        width: Int,
        owner: Identifier?
    ): Int? = when (val element = spec.content) {
        is TextBookElement ->
            BookLinkedTextLayout.lineCount(element.text, Minecraft.getInstance().font, width, owner) *
                Minecraft.getInstance().font.lineHeight
        is CraftingBookElement -> BookRecipeElementRenderer.preferredHeight(element, width, owner)
        is GroupBookElement -> layout(element, width, owner).height
        else -> null
    }

    private fun BookElementSpec.resolveText(owner: Identifier?): BookElementSpec? {
        val element = content as? TextBookElement ?: return this
        val candidates = buildList {
            add(BookTextVariant(element.text, element.requirement))
            addAll(element.variants)
        }
        val selected =
            candidates.lastOrNull { variant ->
                owner != null && variant.requirement?.let { ClientResearchState.requirementMet(owner, it) } == true
            }
                ?: candidates.lastOrNull { it.requirement == null }
                ?: return null
        return copy(content = element.copy(text = selected.text, requirement = null, variants = emptyList()))
    }

    private data class ElementSize(
        val width: Int,
        val height: Int
    )
}
