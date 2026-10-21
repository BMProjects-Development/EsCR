package com.algorithmlx.ecr.api.research.content

import net.minecraft.resources.Identifier

data class BookResearchLink(
    val research: Identifier,
    val spread: Int = 0
) {
    init {
        require(spread >= 0)
    }

    companion object {
        fun parse(value: String, owner: Identifier? = null): BookResearchLink? {
            var link = value.trim()
            if (link.isBlank()) return null

            link = link.removePrefix("research://").removePrefix("book://")

            val fragment = link.substringAfter('#', "")
            if ('#' in link) link = link.substringBefore('#')

            val query = link.substringAfter('?', "")
            if ('?' in link) link = link.substringBefore('?')

            val research = parseResearch(link, owner) ?: return null
            val spread = parseSpread(query) ?: parseSpread(fragment) ?: 0
            return BookResearchLink(research, spread.coerceAtLeast(0))
        }

        private fun parseResearch(value: String, owner: Identifier?): Identifier? {
            if (value.isBlank()) return owner
            val id = if (':' in value || owner == null) value else "${owner.namespace}:$value"
            return Identifier.tryParse(id)
        }

        private fun parseSpread(value: String): Int? {
            if (value.isBlank()) return null
            return value.split('&', ';')
                .asSequence()
                .map(String::trim)
                .filter(String::isNotBlank)
                .firstNotNullOfOrNull(::parseSpreadToken)
        }

        private fun parseSpreadToken(value: String): Int? {
            val key = value.substringBefore('=', "").lowercase()
            val number = value.substringAfter('=', value).toIntOrNull() ?: return null
            return when (key) {
                "spread", "s" -> number
                "page", "p", "" -> number - 1
                else -> null
            }
        }
    }
}
