package com.algorithmlx.ecr.api.research.content

import com.algorithmlx.ecr.api.research.OpenResearchTask
import com.algorithmlx.ecr.api.research.ResearchIds
import com.algorithmlx.ecr.api.research.ResearchTask
import net.minecraft.resources.Identifier

data class BookEntry(
    val id: Identifier,
    val title: BookText,
    val description: BookText? = null,
    val category: Identifier? = null,
    val icon: BookIcon = BookIcon(),
    val frame: BookFrame? = BookFrame(),
    val position: BookPosition? = null,
    val dependencies: List<Identifier> = emptyList(),
    val requirements: List<ResearchRequirement> = emptyList(),
    val pages: List<BookPage> = emptyList(),
    val taskLevels: List<ResearchTaskLevel> = emptyList(),
    val taskIcons: Map<String, BookIcon> = emptyMap(),
    val locks: List<ResearchLock> = emptyList(),
    val automatic: Boolean = taskLevels.isEmpty(),
    val hiddenUntilAvailable: Boolean = false,
    val titleShadow: Boolean = false,
    val align: Set<BookEntryAlign> = emptySet(),
    val link: BookEntryLink? = null
) {
    val taskDefinitions: List<ResearchTaskDefinition> = taskLevels.flatMap(ResearchTaskLevel::tasks)
    val tasks: List<ResearchTask> = taskDefinitions.map(ResearchTaskDefinition::task)

    init {
        val ids = taskLevels.map(ResearchTaskLevel::id) + taskDefinitions.map(ResearchTaskDefinition::id)
        require(ids.distinct().size == ids.size) { "Task level and task IDs must be unique in $id" }
        require(link == null || pages.isEmpty()) { "Research link $id must not contain pages" }
    }
}

sealed interface BookEntryLink {
    data class Category(
        val category: Identifier
    ) : BookEntryLink

    data class Research(
        val research: Identifier
    ) : BookEntryLink

    data class Page(
        val research: Identifier,
        val spread: Int
    ) : BookEntryLink {
        init {
            require(spread >= 0)
        }
    }
}

data class ResearchRequirement(
    val research: Identifier? = null,
    val task: String? = null
) {
    init {
        require(research != null || !task.isNullOrBlank())
    }

    fun researchId(owner: Identifier): Identifier = research ?: owner
}

data class ResearchTaskLevel(
    val id: String,
    val tasks: List<ResearchTaskDefinition>
) {
    init {
        require(id.isNotBlank())
        require(tasks.isNotEmpty())
    }
}

data class ResearchTaskDefinition(
    val id: String,
    val task: ResearchTask,
    val title: BookText? = null,
    val hidden: Boolean = task is OpenResearchTask
) {
    init {
        require(id.isNotBlank())
    }
}

enum class BookEntryAlign {
    UP,
    DOWN,
    LEFT,
    RIGHT
}

data class BookFrame(
    val texture: Identifier = ResearchIds.DEFAULT_FRAME,
    val width: Int = 32,
    val height: Int = 32,
    val itemX: Int = 8,
    val itemY: Int = 8,
    val itemSize: Int = 16
) {
    init {
        require(width > 0 && height > 0 && itemSize >= 0)
    }
}

data class BookPosition(val x: Int, val y: Int)

data class BookPage(val elements: List<BookElementSpec>)

data class BookElementSpec(
    val content: BookElement,
    val width: Int? = null,
    val height: Int? = null,
    val align: BookElementAlign = BookElementAlign.LEFT
)

enum class BookElementAlign {
    LEFT,
    CENTER,
    RIGHT
}

data class ResolvedBookEntry(
    val entry: BookEntry,
    val category: Identifier,
    val position: BookPosition
)

enum class ResearchTargetType {
    ITEM,
    BLOCK,
    ENTITY
}

enum class ResearchAction {
    USE,
    ATTACK,
    BREAK,
    PLACE,
    INTERACT
}

data class ResearchLock(
    val targetType: ResearchTargetType,
    val target: Identifier,
    val actions: Set<ResearchAction> = setOf(ResearchAction.USE, ResearchAction.INTERACT)
)
