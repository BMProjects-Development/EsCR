package com.algorithmlx.ecr.api.research.content

import net.minecraft.resources.Identifier

data class BookCategory(
    val id: Identifier,
    val title: BookText,
    val icon: BookIcon = BookIcon(),
    val order: Int = 0,
    val background: Identifier? = null,
    val dependencies: Set<Identifier> = emptySet(),
    val bookLevel: Identifier? = null,
    val shader: BookShader? = null,
    val threadColor: Int? = null,
    val titleShadow: Boolean = false
)

data class BookShader(
    val vertex: Identifier,
    val fragment: Identifier = vertex
)

data class BookIcon(
    val item: Identifier? = null,
    val texture: Identifier? = null
) {
    init {
        require(item == null || texture == null)
    }
}

data class BookText(
    val value: String,
    val translated: Boolean = true
)
