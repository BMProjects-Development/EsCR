package com.algorithmlx.ecr.api.research

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.research.content.BookCategory
import com.algorithmlx.ecr.api.research.content.BookElementAlign
import com.algorithmlx.ecr.api.research.content.BookElementSpec
import com.algorithmlx.ecr.api.research.content.BookEntry
import com.algorithmlx.ecr.api.research.content.BookEntryAlign
import com.algorithmlx.ecr.api.research.content.BookEntryLink
import com.algorithmlx.ecr.api.research.content.BookFrame
import com.algorithmlx.ecr.api.research.content.BookIcon
import com.algorithmlx.ecr.api.research.content.BookPage
import com.algorithmlx.ecr.api.research.content.BookPosition
import com.algorithmlx.ecr.api.research.content.BookResearchLink
import com.algorithmlx.ecr.api.research.content.BookShader
import com.algorithmlx.ecr.api.research.content.ResearchAction
import com.algorithmlx.ecr.api.research.content.ResearchLock
import com.algorithmlx.ecr.api.research.content.ResearchRequirement
import com.algorithmlx.ecr.api.research.content.ResearchTargetType
import com.algorithmlx.ecr.api.research.content.ResearchTaskDefinition
import com.algorithmlx.ecr.api.research.content.ResearchTaskLevel
import com.algorithmlx.ecr.api.research.serializer.ResearchSerializers
import com.algorithmlx.ecr.api.research.serializer.researchJson
import com.algorithmlx.ecr.api.research.serializer.toBookText
import com.algorithmlx.ecr.api.research.serializer.toJsonElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import net.minecraft.resources.Identifier

object ResearchJson {
    fun decodeCategory(id: Identifier, json: JsonObject): BookCategory {
        val dto = researchJson.decodeFromJsonElement<CategoryDto>(json)
        return BookCategory(
            id = id,
            title = dto.title.toBookText(),
            icon = dto.icon.toModel(),
            order = dto.order,
            background = dto.background?.let(Identifier::parse),
            dependencies = dto.dependencies.mapTo(LinkedHashSet()) { parseReference(it, id) },
            bookLevel = dto.bookLevel?.let(Identifier::parse),
            shader = dto.shader?.toShader(),
            threadColor = dto.threadColor?.toColor(),
            titleShadow = dto.shadow
        )
    }

    fun decodeEntry(id: Identifier, json: JsonObject): BookEntry {
        val dto = researchJson.decodeFromJsonElement<EntryDto>(json)
        val taskLevels = decodeTaskLevels(dto)
        return BookEntry(
            id = id,
            title = dto.title.toBookText(),
            description = dto.description?.toBookText()?.takeUnless { it.value.isBlank() },
            category = dto.category?.let { parseReference(it, id) },
            icon = dto.icon.toModel(),
            frame = dto.frame.toFrame(),
            position = dto.position?.let { BookPosition(it.x, it.y) },
            dependencies = dto.dependencies.map { parseReference(it, id) },
            requirements = dto.requirements.map { parseRequirement(it, id) },
            pages = dto.pages.map { page -> BookPage(page.elements.map(::decodeElementSpec)) },
            taskLevels = taskLevels,
            taskIcons = dto.taskIcons.mapValues { it.value.toModel() },
            locks = dto.locks.map { it.toModel() },
            automatic = dto.automatic ?: taskLevels.isEmpty(),
            hiddenUntilAvailable = dto.hiddenUntilAvailable,
            titleShadow = dto.shadow,
            align = dto.align.mapTo(LinkedHashSet()) { BookEntryAlign.valueOf(it.uppercase()) },
            link = dto.link?.let { decodeEntryLink(it, id) }
        )
    }

    fun encodeCategory(category: BookCategory): JsonObject = researchJson.encodeToJsonElement(
        CategoryDto(
            title = category.title.toJsonElement(),
            icon = category.icon.toDto(),
            order = category.order,
            background = category.background?.toString(),
            dependencies = category.dependencies.map(Identifier::toString),
            bookLevel = category.bookLevel?.toString(),
            shader = category.shader?.toJsonElement(),
            threadColor = category.threadColor?.let { JsonPrimitive("#%08X".format(it)) },
            shadow = category.titleShadow
        )
    ).jsonObject

    fun encodeEntry(entry: BookEntry): JsonObject = researchJson.encodeToJsonElement(
        EntryDto(
            title = entry.title.toJsonElement(),
            description = entry.description?.toJsonElement(),
            category = entry.category?.toString(),
            icon = entry.icon.toDto(),
            frame = entry.frame.toJsonElement(),
            position = entry.position?.let { PositionDto(it.x, it.y) },
            dependencies = entry.dependencies.map(Identifier::toString),
            requirements = entry.requirements.map { it.toReference(entry.id) },
            pages = entry.pages.map { page -> PageDto(page.elements.map(::encodeElementSpec)) },
            taskLevels = entry.taskLevels.map { level ->
                TaskLevelDto(
                    level.id,
                    level.tasks.map { definition ->
                        ResearchSerializers.encodeTask(definition.task)
                            .with("type", JsonPrimitive(definition.task.type.toString()))
                            .with("id", JsonPrimitive(definition.id))
                            .withOptional("title", definition.title?.toJsonElement())
                            .with("hidden", JsonPrimitive(definition.hidden))
                    }
                )
            },
            taskIcons = entry.taskIcons.mapValues { it.value.toDto() },
            locks = entry.locks.map(ResearchLock::toDto),
            automatic = entry.automatic,
            hiddenUntilAvailable = entry.hiddenUntilAvailable,
            shadow = entry.titleShadow,
            align = entry.align.mapTo(LinkedHashSet()) { it.name.lowercase() },
            link = entry.link?.let(::encodeEntryLink)
        )
    ).jsonObject

    fun encodeCatalog(categories: Collection<BookCategory>, entries: Collection<BookEntry>): String = researchJson.encodeToString(
        CatalogDto(
            categories.associate { it.id.toString() to encodeCategory(it) },
            entries.associate { it.id.toString() to encodeEntry(it) }
        )
    )

    fun decodeCatalog(json: String): Pair<List<BookCategory>, List<BookEntry>> {
        val dto = researchJson.decodeFromString<CatalogDto>(json)
        return dto.categories.map { (id, value) -> decodeCategory(Identifier.parse(id), value) } to
            dto.entries.map { (id, value) -> decodeEntry(Identifier.parse(id), value) }
    }

    fun decodeTaskIcons(json: JsonObject): Map<String, BookIcon> {
        val source = json["icons"]?.jsonObject ?: json
        return source.mapValues { (_, value) -> researchJson.decodeFromJsonElement<IconDto>(value).toModel() }
    }

    fun parseRequirement(value: String, owner: Identifier?): ResearchRequirement {
        if (':' !in value) return ResearchRequirement(owner, value)
        val separator = value.lastIndexOf('.')
        val colon = value.indexOf(':')
        return if (separator > colon) {
            ResearchRequirement(Identifier.parse(value.substring(0, separator)), value.substring(separator + 1))
        } else {
            ResearchRequirement(Identifier.parse(value))
        }
    }

    private fun decodeTaskLevels(dto: EntryDto): List<ResearchTaskLevel> {
        val levels = if (dto.taskLevels.isNotEmpty()) {
            dto.taskLevels
        } else if (dto.tasks.isNotEmpty()) {
            listOf(TaskLevelDto("default", dto.tasks))
        } else {
            emptyList()
        }
        return levels.mapIndexed { levelIndex, level ->
            ResearchTaskLevel(
                level.id,
                level.tasks.mapIndexed { taskIndex, task ->
                    val taskId = task["id"]?.jsonPrimitive?.content ?: "task_${levelIndex}_$taskIndex"
                    val decoded = ResearchSerializers.decodeTask(task.typeIdentifier(), task)
                    ResearchTaskDefinition(
                        taskId,
                        decoded,
                        task["title"]?.toBookText(),
                        task["hidden"]?.jsonPrimitive?.booleanOrNull ?: (decoded is OpenResearchTask)
                    )
                }
            )
        }
    }

    internal fun decodeElementSpec(json: JsonObject): BookElementSpec = BookElementSpec(
        ResearchSerializers.decodeElement(json.typeIdentifier(), json),
        json["width"]?.jsonPrimitive?.intOrNull,
        json["height"]?.jsonPrimitive?.intOrNull,
        json["align"]?.jsonPrimitive?.content
            ?.let { BookElementAlign.valueOf(it.uppercase()) }
            ?: BookElementAlign.LEFT
    )

    internal fun encodeElementSpec(spec: BookElementSpec): JsonObject = ResearchSerializers.encodeElement(spec.content)
        .with("type", JsonPrimitive(spec.content.type.toString()))
        .let { value -> spec.width?.let { value.with("width", JsonPrimitive(it)) } ?: value }
        .let { value -> spec.height?.let { value.with("height", JsonPrimitive(it)) } ?: value }
        .let { value ->
            if (spec.align == BookElementAlign.LEFT) value
            else value.with("align", JsonPrimitive(spec.align.name.lowercase()))
        }

    private fun JsonObject.typeIdentifier(): Identifier {
        val value = getValue("type").jsonPrimitive.content
        return Identifier.parse(if (':' in value) value else "$ModId:$value")
    }

    private fun parseReference(value: String, owner: Identifier): Identifier =
        Identifier.parse(if (':' in value) value else "${owner.namespace}:$value")

    private fun decodeEntryLink(value: JsonElement, owner: Identifier): BookEntryLink {
        if (value is JsonPrimitive && value.isString) return decodeEntryLink(value.content, owner)

        val link = value.jsonObject
        val explicitType = link["type"]?.jsonPrimitive?.content?.lowercase()
        val inferredType = explicitType ?: when {
            "category" in link -> "category"
            "page" in link || "spread" in link -> "page"
            else -> "research"
        }
        val target =
            link["target"]?.jsonPrimitive?.content
                ?: link[if (inferredType == "category") "category" else "research"]?.jsonPrimitive?.content
                ?: error("Research link requires target")
        val id = parseReference(target, owner)
        return when (inferredType) {
            "category" -> BookEntryLink.Category(id)
            "research" -> BookEntryLink.Research(id)
            "page" -> {
                val spread =
                    link["spread"]?.jsonPrimitive?.intOrNull
                        ?: link["page"]?.jsonPrimitive?.intOrNull?.minus(1)
                        ?: 0
                BookEntryLink.Page(id, spread)
            }
            else -> error("Unknown research link type '$inferredType'")
        }
    }

    private fun decodeEntryLink(value: String, owner: Identifier): BookEntryLink {
        val source = value.trim()
        require(source.isNotEmpty()) { "Research link must not be blank" }
        if (source.startsWith("category://")) {
            return BookEntryLink.Category(parseReference(source.removePrefix("category://"), owner))
        }
        val parsed =
            BookResearchLink.parse(source.replaceFirst("page://", "research://"), owner)
                ?: error("Invalid research link '$value'")
        val pointsToPage = '?' in source || '#' in source || source.startsWith("page://")
        return if (pointsToPage) {
            BookEntryLink.Page(parsed.research, parsed.spread)
        } else {
            BookEntryLink.Research(parsed.research)
        }
    }

    private fun encodeEntryLink(link: BookEntryLink): JsonElement =
        buildJsonObject {
            when (link) {
                is BookEntryLink.Category -> {
                    put("type", "category")
                    put("target", link.category.toString())
                }
                is BookEntryLink.Research -> {
                    put("type", "research")
                    put("target", link.research.toString())
                }
                is BookEntryLink.Page -> {
                    put("type", "page")
                    put("target", link.research.toString())
                    put("page", link.spread + 1)
                }
            }
        }

    private fun ResearchRequirement.toReference(owner: Identifier): String = when {
        research == null || research == owner -> task ?: owner.toString()
        task == null -> research.toString()
        else -> "$research.$task"
    }

    private fun JsonElement.toFrame(): BookFrame? = when (this) {
        is JsonNull -> null
        is JsonPrimitive if booleanOrNull == false -> null
        is JsonPrimitive if booleanOrNull == true -> BookFrame()
        else -> researchJson.decodeFromJsonElement<FrameDto>(this).toModel()
    }

    private fun BookFrame?.toJsonElement(): JsonElement = this?.let {
        researchJson.encodeToJsonElement(FrameDto(it.texture.toString(), it.width, it.height, it.itemX, it.itemY, it.itemSize))
    } ?: JsonNull

    private fun JsonElement.toShader(): BookShader = when (this) {
        is JsonPrimitive -> BookShader(Identifier.parse(content))
        else -> researchJson.decodeFromJsonElement<ShaderDto>(this).let {
            val vertex = Identifier.parse(it.vertex ?: it.fragment ?: error("Shader requires vertex or fragment"))
            BookShader(
                vertex,
                Identifier.parse(it.fragment ?: it.vertex ?: error("Shader requires vertex or fragment"))
            )
        }
    }

    private fun BookShader.toJsonElement(): JsonElement = researchJson.encodeToJsonElement(
        ShaderDto(vertex.toString(), fragment.toString())
    )

    private fun JsonElement.toColor(): Int = when (this) {
        is JsonPrimitive -> {
            intOrNull ?: content.removePrefix("#").let { hex ->
                val value = hex.toLong(16)
                when (hex.length) {
                    6 -> (0xFF000000L or value).toInt()
                    8 -> value.toInt()
                    else -> error("Color must be #RRGGBB, #AARRGGBB, or integer")
                }
            }
        }
        else -> error("Color must be #RRGGBB, #AARRGGBB, or integer")
    }

    private fun JsonObject.with(name: String, value: JsonElement): JsonObject = JsonObject(this + (name to value))
    private fun JsonObject.withOptional(name: String, value: JsonElement?): JsonObject =
        value?.let { with(name, it) } ?: this
}

@Serializable
private data class CatalogDto(
    val categories: Map<String, JsonObject>,
    val entries: Map<String, JsonObject>
)

@Serializable
private data class CategoryDto(
    val title: JsonElement,
    val icon: IconDto = IconDto(),
    val order: Int = 0,
    val background: String? = null,
    val dependencies: List<String> = emptyList(),
    @SerialName("book_level") val bookLevel: String? = null,
    val shader: JsonElement? = null,
    @SerialName("thread_color") val threadColor: JsonElement? = null,
    val shadow: Boolean = false
)

@Serializable
private data class EntryDto(
    val title: JsonElement,
    val description: JsonElement? = null,
    val category: String? = null,
    val icon: IconDto = IconDto(),
    val frame: JsonElement = JsonPrimitive(true),
    val position: PositionDto? = null,
    val align: Set<String> = emptySet(),
    val dependencies: List<String> = emptyList(),
    val requirements: List<String> = emptyList(),
    val pages: List<PageDto> = emptyList(),
    val tasks: List<JsonObject> = emptyList(),
    @SerialName("task_levels") val taskLevels: List<TaskLevelDto> = emptyList(),
    @SerialName("task_icons") val taskIcons: Map<String, IconDto> = emptyMap(),
    val locks: List<LockDto> = emptyList(),
    val automatic: Boolean? = null,
    @SerialName("hidden_until_available") val hiddenUntilAvailable: Boolean = false,
    val shadow: Boolean = false,
    val link: JsonElement? = null
)

@Serializable
private data class IconDto(val item: String? = null, val texture: String? = null) {
    fun toModel() = BookIcon(item?.let(Identifier::parse), texture?.let(Identifier::parse))
}

@Serializable
private data class PositionDto(val x: Int = 0, val y: Int = 0)

@Serializable
private data class PageDto(val elements: List<JsonObject> = emptyList())

@Serializable
private data class TaskLevelDto(
    val id: String,
    val tasks: List<JsonObject>
)

@Serializable
private data class FrameDto(
    val texture: String = ResearchIds.DEFAULT_FRAME.toString(),
    val width: Int = 32,
    val height: Int = 32,
    @SerialName("item_x") val itemX: Int = 8,
    @SerialName("item_y") val itemY: Int = 8,
    @SerialName("item_size") val itemSize: Int = 16
) {
    fun toModel() = BookFrame(Identifier.parse(texture), width, height, itemX, itemY, itemSize)
}

@Serializable
private data class LockDto(val type: String, val id: String, val actions: Set<String> = emptySet()) {
    fun toModel() = ResearchLock(
        ResearchTargetType.valueOf(type.uppercase()),
        Identifier.parse(id),
        actions.mapTo(LinkedHashSet()) { ResearchAction.valueOf(it.uppercase()) }
            .ifEmpty { setOf(ResearchAction.USE, ResearchAction.INTERACT) }
    )
}

@Serializable
private data class ShaderDto(val vertex: String? = null, val fragment: String? = null)

private fun BookIcon.toDto() = IconDto(item?.toString(), texture?.toString())
private fun ResearchLock.toDto() = LockDto(targetType.name.lowercase(), target.toString(), actions.mapTo(LinkedHashSet()) { it.name.lowercase() })
