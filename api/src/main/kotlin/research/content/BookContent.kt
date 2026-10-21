package com.algorithmlx.ecr.api.research.content

import com.algorithmlx.ecr.api.multiblock.Multiblock
import com.algorithmlx.ecr.api.multiblock.MultiblockDataReloadListener
import com.algorithmlx.ecr.api.research.ResearchIds
import com.google.gson.JsonParser
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import net.minecraft.resources.Identifier

interface BookElement {
    val type: Identifier
}

interface BookElementSerializer<T : BookElement> {
    val type: Identifier
    val defaultWidth: Int
    val defaultHeight: Int

    fun decode(json: JsonObject): T

    fun encode(value: T): JsonObject
}

data object SpaceBookElement : BookElement {
    override val type: Identifier = ResearchIds.SPACE
}

data object VerticalSpaceBookElement : BookElement {
    override val type: Identifier = ResearchIds.VERTICAL_SPACE
}

data class TaskListBookElement(
    val research: Identifier,
    val level: Int
) : BookElement {
    override val type: Identifier = ResearchIds.TASK_LIST
}

data class TextBookElement(
    val text: BookText,
    val color: Int = 0xFF202020.toInt(),
    val centered: Boolean = false,
    val shadow: Boolean = false,
    val requirement: BookTextRequirement? = null,
    val variants: List<BookTextVariant> = emptyList()
) : BookElement {
    override val type: Identifier = ResearchIds.TEXT
}

data class BookTextVariant(
    val text: BookText,
    val requirement: BookTextRequirement? = null
)

typealias BookTextRequirement = ResearchRequirement

data class ItemBookElement(
    val item: Identifier,
    var count: Int = 1,
    val tooltip: Boolean = false
) : BookElement {
    override val type: Identifier = ResearchIds.ITEM

    init {
        count = count.coerceAtLeast(1)
    }
}

data class BlockBookElement(
    val block: Identifier
) : BookElement {
    override val type: Identifier = ResearchIds.BLOCK
}

data class GroupBookElement(
    val elements: List<BookElementSpec>
) : BookElement {
    override val type: Identifier = ResearchIds.GROUP
}

data class MultiblockBookElement(
    val multiblock: Identifier,
    val scale: Float = 0.9F,
    val rotationX: Float = 25F,
    val rotationY: Float = -30F,
    val layer: Int = Int.MAX_VALUE
) : BookElement {
    override val type: Identifier = ResearchIds.MULTIBLOCK
}

data class BookMultiblockElement(
    val pattern: List<List<String>>,
    val key: JsonObject,
    val scale: Float = 0.9F,
    val rotationX: Float = 25F,
    val rotationY: Float = -30F,
    val layer: Int = Int.MAX_VALUE
) : BookElement {
    override val type: Identifier = ResearchIds.BOOK_MULTIBLOCK

    val multiblock: Multiblock =
        MultiblockDataReloadListener().decodeMultiblock(
            JsonParser.parseString(
                buildJsonObject {
                    put(
                        "pattern",
                        JsonArray(pattern.map { rows -> JsonArray(rows.map(::JsonPrimitive)) })
                    )
                    put("keys", key)
                }.toString()
            ).asJsonObject
        )
}

data class AssembledMultiblockBookElement(
    val multiblock: Identifier,
    val assembled: Boolean = false,
    val scale: Float = 0.9F,
    val rotationX: Float = 25F,
    val rotationY: Float = -30F,
    val layer: Int = Int.MAX_VALUE
) : BookElement {
    override val type: Identifier = ResearchIds.ASSEMBLED_MULTIBLOCK
}

data class CraftingBookElement(
    val recipe: Identifier
) : BookElement {
    override val type: Identifier = ResearchIds.RECIPE
}
