package com.algorithmlx.ecr.api.research

import com.algorithmlx.ecr.api.research.content.BookElementSpec
import com.algorithmlx.ecr.api.research.content.BookEntry
import com.algorithmlx.ecr.api.research.content.BookText
import com.algorithmlx.ecr.api.research.content.GroupBookElement
import com.algorithmlx.ecr.api.research.content.TextBookElement

internal data class ResearchTextContent(
    val title: BookText,
    val description: BookText?,
    val taskTitles: List<BookText?>,
    val pages: List<List<BookText>>
)

internal fun BookEntry.textContent(): ResearchTextContent = ResearchTextContent(
    title,
    description,
    taskDefinitions.map { definition -> definition.title },
    pages.map { page -> page.elements.flatMap(BookElementSpec::texts) }
)

private fun BookElementSpec.texts(): List<BookText> = when (val element = content) {
    is TextBookElement -> buildList {
        add(element.text)
        element.variants.mapTo(this) { variant -> variant.text }
    }
    is GroupBookElement -> element.elements.flatMap(BookElementSpec::texts)
    else -> emptyList()
}
