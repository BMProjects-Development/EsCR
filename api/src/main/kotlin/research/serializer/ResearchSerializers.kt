@file:Suppress("ktlint:standard:no-wildcard-imports")

package com.algorithmlx.ecr.api.research.serializer

import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.research.*
import com.algorithmlx.ecr.api.research.content.*
import com.algorithmlx.ecr.api.utils.rl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import net.minecraft.resources.Identifier

val researchJson =
    Json {
        allowComments = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

object ResearchSerializers {
    @JvmField val SPACE_ELEMENT: BookElementSerializer<SpaceBookElement> = SpaceElementSerializer

    @JvmField val VERTICAL_SPACE_ELEMENT: BookElementSerializer<VerticalSpaceBookElement> = VerticalSpaceElementSerializer

    @JvmField val TEXT_ELEMENT: BookElementSerializer<TextBookElement> = TextElementSerializer

    @JvmField val ITEM_ELEMENT: BookElementSerializer<ItemBookElement> = ItemElementSerializer

    @JvmField val BLOCK_ELEMENT: BookElementSerializer<BlockBookElement> = BlockElementSerializer

    @JvmField val GROUP_ELEMENT: BookElementSerializer<GroupBookElement> = GroupElementSerializer

    @JvmField val MULTIBLOCK_ELEMENT: BookElementSerializer<MultiblockBookElement> = MultiblockElementSerializer

    @JvmField val BOOK_MULTIBLOCK_ELEMENT: BookElementSerializer<BookMultiblockElement> = BookMultiblockElementSerializer

    @JvmField val ASSEMBLED_MULTIBLOCK_ELEMENT: BookElementSerializer<AssembledMultiblockBookElement> =
        AssembledMultiblockElementSerializer

    @JvmField val CRAFTING_ELEMENT: BookElementSerializer<CraftingBookElement> = CraftingElementSerializer

    @JvmField val ITEM_TASK: ResearchTaskSerializer<ItemResearchTask> = ItemTaskSerializer

    @JvmField val EXPERIENCE_TASK: ResearchTaskSerializer<ExperienceResearchTask> = ExperienceTaskSerializer

    @JvmField val CRAFTING_TASK: ResearchTaskSerializer<CraftingResearchTask> = CraftingTaskSerializer

    @JvmField val OPEN_TASK: ResearchTaskSerializer<OpenResearchTask> = OpenTaskSerializer

    @JvmField val TRAVEL_TO_DIMENSION: ResearchTaskSerializer<TravelToDimensionResearchTask> = TravelToDimension

    @JvmField val TRAVEL_TO_STRUCTURE: ResearchTaskSerializer<TravelToStructureResearchTask> = TravelToStructure

    @Suppress("UNCHECKED_CAST")
    fun decodeElement(
        type: Identifier,
        json: JsonObject
    ): BookElement =
        (elementSerializer(type) as? BookElementSerializer<BookElement>)?.decode(json)
            ?: error("Unknown book element type: $type")

    @Suppress("UNCHECKED_CAST")
    fun encodeElement(value: BookElement): JsonObject =
        (elementSerializer(value.type) as? BookElementSerializer<BookElement>)?.encode(value)
            ?: error("Unknown book element type: ${value.type}")

    @Suppress("UNCHECKED_CAST")
    fun decodeTask(
        type: Identifier,
        json: JsonObject
    ): ResearchTask =
        (taskSerializer(type) as? ResearchTaskSerializer<ResearchTask>)?.decode(json)
            ?: error("Unknown research task type: $type")

    @Suppress("UNCHECKED_CAST")
    fun encodeTask(value: ResearchTask): JsonObject =
        (taskSerializer(value.type) as? ResearchTaskSerializer<ResearchTask>)?.encode(value)
            ?: error("Unknown research task type: ${value.type}")

    fun elementSerializer(type: Identifier): BookElementSerializer<*>? = ECRegistries.BOOK_ELEMENT_SERIALIZER.getOptional(type).orElse(null)

    fun taskSerializer(type: Identifier): ResearchTaskSerializer<*>? = ECRegistries.RESEARCH_TASK_SERIALIZER.getOptional(type).orElse(null)
}

@Serializable
private data class TextElementDto(
    val text: JsonElement,
    val color: Int = 0xFF202020.toInt(),
    val centered: Boolean = false,
    val shadow: Boolean = false,
    val requirement: TextRequirementDto? = null,
    val variants: List<TextVariantDto> = emptyList()
)

@Serializable
private data class TextVariantDto(
    val text: JsonElement,
    val requirement: TextRequirementDto? = null
)

@Serializable
private data class TextRequirementDto(
    val task: String? = null,
    val research: String? = null,
    val requirement: String? = null
)

@Serializable
private data class ItemElementDto(
    val item: String,
    val count: Int = 1,
    val tooltip: Boolean = false
)

@Serializable
private data class BlockElementDto(
    val block: String
)

@Serializable
private data class GroupElementDto(
    val elements: List<JsonObject> = emptyList()
)

@Serializable
private data class MultiblockElementDto(
    val multiblock: String,
    val scale: Float = 0.9f,
    @SerialName("rotation_x") val rotationX: Float = 25f,
    @SerialName("rotation_y") val rotationY: Float = -30f,
    val layer: Int = Int.MAX_VALUE
)

@Serializable
private data class BookMultiblockElementDto(
    val pattern: List<List<String>>,
    val key: JsonObject? = null,
    val keys: JsonObject? = null,
    val scale: Float = 0.9f,
    @SerialName("rotation_x") val rotationX: Float = 25f,
    @SerialName("rotation_y") val rotationY: Float = -30f,
    val layer: Int = Int.MAX_VALUE
)

@Serializable
private data class AssembledMultiblockElementDto(
    val multiblock: String,
    val assembled: Boolean = false,
    val scale: Float = 0.9f,
    @SerialName("rotation_x") val rotationX: Float = 25f,
    @SerialName("rotation_y") val rotationY: Float = -30f,
    val layer: Int = Int.MAX_VALUE
)

@Serializable
private data class CraftingElementDto(
    val recipe: String
)

@Serializable
private data class ItemTaskDto(
    val item: String,
    val count: Int = 1,
    val consume: Boolean = false,
    val components: JsonObject = JsonObject(emptyMap())
)

@Serializable
private data class ExperienceTaskDto(
    val amount: Int = 1,
    val levels: Boolean = false,
    val consume: Boolean = false
)

@Serializable
private data class CraftingTaskDto(
    val recipe: String
)

@Serializable
private data class OpenTaskDto(
    val research: String? = null
)

@Serializable
private data class TravelToDimensionDto(
    val dimension: String
)

private data class TravelToStructureDto(
    val id: String? = null,
    val tag: String? = null
)

private object SpaceElementSerializer : BookElementSerializer<SpaceBookElement> {
    override val type = ResearchIds.SPACE
    override val defaultWidth = 0
    override val defaultHeight = 8

    override fun decode(json: JsonObject) = SpaceBookElement

    override fun encode(value: SpaceBookElement) = JsonObject(emptyMap())
}

private object VerticalSpaceElementSerializer : BookElementSerializer<VerticalSpaceBookElement> {
    override val type = ResearchIds.VERTICAL_SPACE
    override val defaultWidth = 8
    override val defaultHeight = 0

    override fun decode(json: JsonObject) = VerticalSpaceBookElement

    override fun encode(value: VerticalSpaceBookElement) = JsonObject(emptyMap())
}

private object TextElementSerializer : BookElementSerializer<TextBookElement> {
    override val type = ResearchIds.TEXT
    override val defaultWidth = 208
    override val defaultHeight = 18

    override fun decode(json: JsonObject): TextBookElement =
        researchJson.decodeFromJsonElement<TextElementDto>(json).let {
            TextBookElement(
                it.text.toBookText(),
                it.color,
                it.centered,
                it.shadow,
                it.requirement?.toModel(),
                it.variants.map { variant ->
                    BookTextVariant(
                        variant.text.toBookText(),
                        variant.requirement?.toModel()
                    )
                }
            )
        }

    override fun encode(value: TextBookElement): JsonObject =
        researchJson
            .encodeToJsonElement(
                TextElementDto(
                    value.text.toJsonElement(),
                    value.color,
                    value.centered,
                    value.shadow,
                    value.requirement?.toDto(),
                    value.variants.map { TextVariantDto(it.text.toJsonElement(), it.requirement?.toDto()) }
                )
            ).jsonObject
}

private object ItemElementSerializer : BookElementSerializer<ItemBookElement> {
    override val type = ResearchIds.ITEM
    override val defaultWidth = 18
    override val defaultHeight = 18

    override fun decode(json: JsonObject): ItemBookElement = researchJson.decodeFromJsonElement<ItemElementDto>(json).toElement()

    override fun encode(value: ItemBookElement): JsonObject = researchJson.encodeToJsonElement(value.toDto()).jsonObject
}

private object BlockElementSerializer : BookElementSerializer<BlockBookElement> {
    override val type = ResearchIds.BLOCK
    override val defaultWidth = 18
    override val defaultHeight = 18

    override fun decode(json: JsonObject): BlockBookElement =
        BlockBookElement(
            researchJson.decodeFromJsonElement<BlockElementDto>(json).block.rl
        )

    override fun encode(value: BlockBookElement): JsonObject =
        researchJson
            .encodeToJsonElement(
                BlockElementDto(value.block.toString())
            ).jsonObject
}

private object GroupElementSerializer : BookElementSerializer<GroupBookElement> {
    override val type = ResearchIds.GROUP
    override val defaultWidth = 225
    override val defaultHeight = 0

    override fun decode(json: JsonObject): GroupBookElement =
        researchJson.decodeFromJsonElement<GroupElementDto>(json).let { dto ->
            GroupBookElement(dto.elements.map(ResearchJson::decodeElementSpec))
        }

    override fun encode(value: GroupBookElement): JsonObject =
        researchJson
            .encodeToJsonElement(
                GroupElementDto(value.elements.map(ResearchJson::encodeElementSpec))
            ).jsonObject
}

private object MultiblockElementSerializer : BookElementSerializer<MultiblockBookElement> {
    override val type = ResearchIds.MULTIBLOCK
    override val defaultWidth = 150
    override val defaultHeight = 150

    override fun decode(json: JsonObject): MultiblockBookElement =
        researchJson.decodeFromJsonElement<MultiblockElementDto>(json).let {
            MultiblockBookElement(
                it.multiblock.rl,
                it.scale,
                it.rotationX,
                it.rotationY,
                it.layer
            )
        }

    override fun encode(value: MultiblockBookElement): JsonObject =
        researchJson
            .encodeToJsonElement(
                MultiblockElementDto(value.multiblock.toString(), value.scale, value.rotationX, value.rotationY, value.layer)
            ).jsonObject
}

private object BookMultiblockElementSerializer : BookElementSerializer<BookMultiblockElement> {
    override val type = ResearchIds.BOOK_MULTIBLOCK
    override val defaultWidth = 150
    override val defaultHeight = 150

    override fun decode(json: JsonObject): BookMultiblockElement =
        researchJson.decodeFromJsonElement<BookMultiblockElementDto>(json).let {
            BookMultiblockElement(
                it.pattern,
                it.key ?: it.keys ?: error("book_multiblock requires key"),
                it.scale,
                it.rotationX,
                it.rotationY,
                it.layer
            )
        }

    override fun encode(value: BookMultiblockElement): JsonObject =
        researchJson
            .encodeToJsonElement(
                BookMultiblockElementDto(
                    value.pattern,
                    value.key,
                    scale = value.scale,
                    rotationX = value.rotationX,
                    rotationY = value.rotationY,
                    layer = value.layer
                )
            ).jsonObject
}

private object AssembledMultiblockElementSerializer : BookElementSerializer<AssembledMultiblockBookElement> {
    override val type = ResearchIds.ASSEMBLED_MULTIBLOCK
    override val defaultWidth = 150
    override val defaultHeight = 150

    override fun decode(json: JsonObject): AssembledMultiblockBookElement =
        researchJson.decodeFromJsonElement<AssembledMultiblockElementDto>(json).let {
            AssembledMultiblockBookElement(
                it.multiblock.rl,
                it.assembled,
                it.scale,
                it.rotationX,
                it.rotationY,
                it.layer
            )
        }

    override fun encode(value: AssembledMultiblockBookElement): JsonObject =
        researchJson
            .encodeToJsonElement(
                AssembledMultiblockElementDto(
                    value.multiblock.toString(),
                    value.assembled,
                    value.scale,
                    value.rotationX,
                    value.rotationY,
                    value.layer
                )
            ).jsonObject
}

private object CraftingElementSerializer : BookElementSerializer<CraftingBookElement> {
    override val type = ResearchIds.RECIPE
    override val defaultWidth = 160
    override val defaultHeight = 96

    override fun decode(json: JsonObject): CraftingBookElement =
        CraftingBookElement(
            researchJson.decodeFromJsonElement<CraftingElementDto>(json).recipe.rl
        )

    override fun encode(value: CraftingBookElement): JsonObject =
        researchJson
            .encodeToJsonElement(
                CraftingElementDto(value.recipe.toString())
            ).jsonObject
}

private object ItemTaskSerializer : ResearchTaskSerializer<ItemResearchTask> {
    override val type = ResearchIds.ITEM_TASK

    override fun decode(json: JsonObject): ItemResearchTask =
        researchJson.decodeFromJsonElement<ItemTaskDto>(json).let {
            ItemResearchTask(
                it.item,
                it.count.coerceAtLeast(1),
                it.consume,
                it.components
            )
        }

    override fun encode(value: ItemResearchTask): JsonObject =
        researchJson
            .encodeToJsonElement(
                ItemTaskDto(value.item, value.count, value.consumeItems, value.components)
            ).jsonObject
}

private object CraftingTaskSerializer : ResearchTaskSerializer<CraftingResearchTask> {
    override val type = ResearchIds.RECIPE_TASK

    override fun decode(json: JsonObject): CraftingResearchTask =
        CraftingResearchTask(
            researchJson.decodeFromJsonElement<CraftingTaskDto>(json).recipe.rl
        )

    override fun encode(value: CraftingResearchTask): JsonObject =
        researchJson
            .encodeToJsonElement(
                CraftingTaskDto(value.recipe.toString())
            ).jsonObject
}

private object OpenTaskSerializer : ResearchTaskSerializer<OpenResearchTask> {
    override val type = ResearchIds.OPEN_TASK

    override fun decode(json: JsonObject): OpenResearchTask =
        OpenResearchTask(
            researchJson.decodeFromJsonElement<OpenTaskDto>(json).research?.let(Identifier::parse)
        )

    override fun encode(value: OpenResearchTask): JsonObject =
        researchJson
            .encodeToJsonElement(
                OpenTaskDto(value.research?.toString())
            ).jsonObject
}

private object ExperienceTaskSerializer : ResearchTaskSerializer<ExperienceResearchTask> {
    override val type = ResearchIds.EXPERIENCE_TASK

    override fun decode(json: JsonObject): ExperienceResearchTask =
        researchJson.decodeFromJsonElement<ExperienceTaskDto>(json).let {
            ExperienceResearchTask(
                it.amount.coerceAtLeast(1),
                it.levels,
                it.consume
            )
        }

    override fun encode(value: ExperienceResearchTask): JsonObject =
        researchJson
            .encodeToJsonElement(
                ExperienceTaskDto(value.amount, value.levels, value.consumeExperience)
            ).jsonObject
}

private object TravelToDimension : ResearchTaskSerializer<TravelToDimensionResearchTask> {
    override val type: Identifier = ResearchIds.TRAVEL_TO_DIMENSION

    override fun decode(json: JsonObject): TravelToDimensionResearchTask =
        TravelToDimensionResearchTask(
            researchJson.decodeFromJsonElement<TravelToDimensionDto>(json).dimension.rl
        )

    override fun encode(value: TravelToDimensionResearchTask): JsonObject =
        researchJson
            .encodeToJsonElement(
                TravelToDimensionDto(value.dimension.toString())
            ).jsonObject
}

private object TravelToStructure : ResearchTaskSerializer<TravelToStructureResearchTask> {
    override val type: Identifier = ResearchIds.TRAVEL_TO_STRUCTURE

    override fun decode(json: JsonObject): TravelToStructureResearchTask =
        researchJson.decodeFromJsonElement<TravelToStructureDto>(json).let {
            TravelToStructureResearchTask(it.id?.rl, it.tag?.rl)
        }

    override fun encode(value: TravelToStructureResearchTask): JsonObject =
        researchJson
            .encodeToJsonElement(
                TravelToStructureDto(
                    value.structure?.toString(),
                    value.tag?.toString()
                )
            ).jsonObject
}

internal fun JsonElement.toBookText(): BookText {
    if (this is JsonPrimitive && isString) return BookText(content, true)
    val objectValue = jsonObject
    objectValue["translate"]?.jsonPrimitive?.content?.let {
        return BookText(it, true)
    }
    objectValue["text"]?.jsonPrimitive?.content?.let {
        return BookText(it, false)
    }
    error("Book text must contain 'translate' or 'text'")
}

internal fun BookText.toJsonElement(): JsonElement =
    buildJsonObject {
        put(if (translated) "translate" else "text", value)
    }

private fun ItemElementDto.toElement() = ItemBookElement(item.rl, count, tooltip)

private fun ItemBookElement.toDto() = ItemElementDto(item.toString(), count, tooltip)

private fun TextRequirementDto.toModel(): BookTextRequirement {
    requirement?.let { return ResearchJson.parseRequirement(it, null) }
    if (research == null && task?.contains(':') == true) return ResearchJson.parseRequirement(task, null)
    return BookTextRequirement(
        research?.rl,
        task
    )
}

private fun BookTextRequirement.toDto() = TextRequirementDto(task = task, research = research?.toString())
