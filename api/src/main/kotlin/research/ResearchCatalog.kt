package com.algorithmlx.ecr.api.research

import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.research.content.BookCategory
import com.algorithmlx.ecr.api.research.content.BookElement
import com.algorithmlx.ecr.api.research.content.BookElementSpec
import com.algorithmlx.ecr.api.research.content.BookEntry
import com.algorithmlx.ecr.api.research.content.BookEntryAlign
import com.algorithmlx.ecr.api.research.content.BookEntryLink
import com.algorithmlx.ecr.api.research.content.GroupBookElement
import com.algorithmlx.ecr.api.research.content.BookPosition
import com.algorithmlx.ecr.api.research.content.BookTextVariant
import com.algorithmlx.ecr.api.research.content.ResearchRequirement
import com.algorithmlx.ecr.api.research.content.ResearchTaskDefinition
import com.algorithmlx.ecr.api.research.content.ResearchTaskLevel
import com.algorithmlx.ecr.api.research.content.ResolvedBookEntry
import com.algorithmlx.ecr.api.research.content.TextBookElement
import com.algorithmlx.ecr.api.utils.rl
import net.minecraft.resources.Identifier
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList

data class ResearchCatalogSnapshot(
    val categories: Map<Identifier, BookCategory>,
    val entries: Map<Identifier, BookEntry>,
    val layout: Map<Identifier, ResolvedBookEntry>,
    val disabledEntries: Set<Identifier> = emptySet()
) {
    fun entriesIn(category: Identifier): List<ResolvedBookEntry> = layout.values.filter { it.category == category }
}

object ResearchCatalog {
    private val permanentCategories = LinkedHashMap<Identifier, BookCategory>()
    private val permanentEntries = LinkedHashMap<Identifier, BookEntry>()
    private var loadedCategories: Collection<BookCategory> = emptyList()
    private var loadedEntries: Collection<BookEntry> = emptyList()
    private val disabledEntries = LinkedHashSet<Identifier>()
    private val disabledProviders = CopyOnWriteArrayList<() -> Collection<Identifier>>()
    private val reloadListeners = CopyOnWriteArrayList<(ResearchCatalogSnapshot) -> Unit>()

    @Volatile private var current = ResearchCatalogSnapshot(emptyMap(), emptyMap(), emptyMap())

    @JvmStatic
    fun snapshot(): ResearchCatalogSnapshot = current

    @JvmStatic
    @Synchronized
    fun register(category: BookCategory) {
        check(permanentCategories.putIfAbsent(category.id, category) == null) { "Duplicate category: ${category.id}" }
        refresh()
    }

    @JvmStatic
    @Synchronized
    fun register(entry: BookEntry) {
        check(permanentEntries.putIfAbsent(entry.id, entry) == null) { "Duplicate research: ${entry.id}" }
        refresh()
    }

    @JvmStatic
    @Synchronized
    fun disable(research: Identifier) {
        if (disabledEntries.add(research)) refresh()
    }

    @JvmStatic
    fun disable(research: String) {
        disable(research.rl)
    }

    @JvmStatic
    @Synchronized
    fun enable(research: Identifier) {
        if (disabledEntries.remove(research)) refresh()
    }

    @JvmStatic
    fun enable(research: String) {
        enable(research.rl)
    }

    @JvmStatic
    fun isDisabled(research: Identifier): Boolean = research in current.disabledEntries

    @JvmStatic
    fun isDisabled(research: String): Boolean = isDisabled(research.rl)

    @JvmStatic
    @Synchronized
    fun setDisabled(researches: Collection<Identifier>) {
        disabledEntries.clear()
        disabledEntries += researches
        refresh()
    }

    @JvmStatic
    @Synchronized
    fun clearDisabled() {
        if (disabledEntries.isEmpty()) return
        disabledEntries.clear()
        refresh()
    }

    @JvmStatic
    @Synchronized
    fun addDisabledProvider(provider: () -> Collection<Identifier>) {
        disabledProviders += provider
        refresh()
    }

    @JvmStatic
    @Synchronized
    fun refresh() {
        val categoryMap = LinkedHashMap(permanentCategories)
        loadedCategories.forEach { check(categoryMap.put(it.id, it) == null) { "Duplicate category: ${it.id}" } }
        val entryMap = LinkedHashMap(permanentEntries)
        loadedEntries.forEach { check(entryMap.put(it.id, it) == null) { "Duplicate research: ${it.id}" } }
        install(categoryMap, entryMap)
    }

    @JvmStatic
    fun onReload(listener: (ResearchCatalogSnapshot) -> Unit) {
        reloadListeners += listener
    }

    @JvmStatic
    @Synchronized
    fun replace(
        categories: Collection<BookCategory>,
        entries: Collection<BookEntry>
    ) {
        loadedCategories = categories.toList()
        loadedEntries = entries.toList()
        refresh()
    }

    private fun install(
        categoryMap: LinkedHashMap<Identifier, BookCategory>,
        entryMap: LinkedHashMap<Identifier, BookEntry>
    ) {
        val disabled = disabledResearches()
        val filtered = filterDisabled(categoryMap, entryMap, disabled)
        val layout = ResearchLayout.resolve(filtered.categories, filtered.entries)
        current =
            ResearchCatalogSnapshot(
                Collections.unmodifiableMap(filtered.categories),
                Collections.unmodifiableMap(filtered.entries),
                Collections.unmodifiableMap(layout),
                Collections.unmodifiableSet(filtered.disabled)
            )
        reloadListeners.forEach { it(current) }
    }

    @JvmStatic
    fun exportJson(): String = current.let { ResearchJson.encodeCatalog(it.categories.values, it.entries.values) }

    @JvmStatic
    fun importJson(json: String) {
        val (categories, entries) = ResearchJson.decodeCatalog(json)
        synchronized(this) {
            install(
                LinkedHashMap<Identifier, BookCategory>().apply { categories.forEach { put(it.id, it) } },
                LinkedHashMap<Identifier, BookEntry>().apply { entries.forEach { put(it.id, it) } }
            )
        }
    }

    private fun disabledResearches(): Set<Identifier> =
        buildSet {
            addAll(disabledEntries)
            disabledProviders.forEach { provider -> addAll(provider()) }
        }

    private fun filterDisabled(
        categories: LinkedHashMap<Identifier, BookCategory>,
        entries: LinkedHashMap<Identifier, BookEntry>,
        explicitDisabled: Set<Identifier>
    ): FilteredResearchCatalog {
        val activeCategories = LinkedHashMap(categories)
        val activeEntries = LinkedHashMap(entries)
        val disabled = LinkedHashSet(explicitDisabled)
        val knownCategories = categories.keys.toSet()
        val knownEntries = entries.keys.toSet()
        var changed: Boolean
        do {
            changed = false
            val removedEntries =
                activeEntries.values
                    .filter { entry ->
                        entry.id in disabled ||
                            entry.dependencies.any { it in disabled || (it in knownEntries && it !in activeEntries) } ||
                            entry.requirements.any { requirement ->
                                val target = requirement.researchId(entry.id)
                                target in disabled || (target in knownEntries && target !in activeEntries)
                            } ||
                            entry.category?.let { it in knownCategories && it !in activeCategories } == true ||
                            entry.link.targetsUnavailable(activeCategories.keys, activeEntries.keys, knownCategories, knownEntries)
                    }.mapTo(LinkedHashSet(), BookEntry::id)
            if (removedEntries.isNotEmpty()) {
                activeEntries.keys.removeAll(removedEntries)
                disabled += removedEntries
                changed = true
            }

            val removedCategories =
                activeCategories.values
                    .filter { category -> category.dependencies.any { it in disabled || (it in knownEntries && it !in activeEntries) } }
                    .mapTo(LinkedHashSet(), BookCategory::id)
            if (removedCategories.isNotEmpty()) {
                activeCategories.keys.removeAll(removedCategories)
                changed = true
            }
        } while (changed)
        return FilteredResearchCatalog(activeCategories, activeEntries, disabled)
    }

    private data class FilteredResearchCatalog(
        val categories: LinkedHashMap<Identifier, BookCategory>,
        val entries: LinkedHashMap<Identifier, BookEntry>,
        val disabled: Set<Identifier>
    )
}

private fun BookEntryLink?.targetsUnavailable(
    activeCategories: Set<Identifier>,
    activeEntries: Set<Identifier>,
    knownCategories: Set<Identifier>,
    knownEntries: Set<Identifier>
): Boolean = when (this) {
    is BookEntryLink.Category -> category in knownCategories && category !in activeCategories
    is BookEntryLink.Research -> research in knownEntries && research !in activeEntries
    is BookEntryLink.Page -> research in knownEntries && research !in activeEntries
    null -> false
}

private fun BookElementSpec.walkElements(): Sequence<BookElement> = sequence {
    yield(content)
    if (content is GroupBookElement) {
        content.elements.forEach { child -> yieldAll(child.walkElements()) }
    }
}

private object ResearchLayout {
    fun resolve(
        categories: Map<Identifier, BookCategory>,
        entries: Map<Identifier, BookEntry>
    ): LinkedHashMap<Identifier, ResolvedBookEntry> {
        if (entries.isNotEmpty()) require(categories.isNotEmpty()) { "Research entries require at least one category" }
        categories.values.forEach { category ->
            category.dependencies.forEach { require(it in entries) { "Unknown dependency $it in category ${category.id}" } }
            category.bookLevel?.let {
                require(
                    ECRegistries.BOOK_TYPES.containsKey(it)
                ) { "Unknown book level $it in category ${category.id}" }
            }
        }
        entries.values.forEach { entry ->
            entry.category?.let { require(it in categories) { "Unknown category $it in ${entry.id}" } }
            entry.dependencies.forEach { require(it in entries) { "Unknown dependency $it in ${entry.id}" } }
            entry.requirements.forEach { validateRequirement(entry.id, it, entries) }
            validateTextRequirements(entry, entries)
            validateLink(entry, categories, entries)
        }
        validateLinkCycles(entries)
        val result = LinkedHashMap<Identifier, ResolvedBookEntry>()
        val visiting = HashSet<Identifier>()
        entries.keys.forEach { resolveEntry(it, categories, entries, result, visiting) }
        return result
    }

    private fun validateTextRequirements(
        entry: BookEntry,
        entries: Map<Identifier, BookEntry>
    ) {
        entry.pages
            .asSequence()
            .flatMap { it.elements.asSequence() }
            .flatMap(BookElementSpec::walkElements)
            .mapNotNull { it as? TextBookElement }
            .flatMap { element ->
                sequenceOf(element.requirement) + element.variants.asSequence().map(BookTextVariant::requirement)
            }.filterNotNull()
            .filter { it.researchId(entry.id) in entries }
            .forEach { validateRequirement(entry.id, it, entries) }
    }

    private fun validateLink(
        entry: BookEntry,
        categories: Map<Identifier, BookCategory>,
        entries: Map<Identifier, BookEntry>
    ) {
        val link = entry.link ?: return
        require(entry.pages.isEmpty()) { "Research link ${entry.id} must not contain pages" }
        when (link) {
            is BookEntryLink.Category -> {
                require(link.category in categories) { "Unknown linked category ${link.category} in ${entry.id}" }
            }
            is BookEntryLink.Research -> {
                require(link.research in entries) { "Unknown linked research ${link.research} in ${entry.id}" }
            }
            is BookEntryLink.Page -> {
                val target = entries[link.research]
                    ?: error("Unknown linked research ${link.research} in ${entry.id}")
                require(target.link == null) {
                    "Page link ${entry.id} must point to research content, not another link (${target.id})"
                }
            }
        }
    }

    private fun validateLinkCycles(entries: Map<Identifier, BookEntry>) {
        entries.values.forEach { origin ->
            val visited = LinkedHashSet<Identifier>()
            var current = origin
            while (true) {
                check(visited.add(current.id)) { "Cyclic research link at ${current.id}" }
                val target = when (val link = current.link) {
                    is BookEntryLink.Research -> link.research
                    is BookEntryLink.Page -> link.research
                    else -> break
                }
                current = entries.getValue(target)
            }
        }
    }

    private fun validateRequirement(
        owner: Identifier,
        requirement: ResearchRequirement,
        entries: Map<Identifier, BookEntry>
    ) {
        val targetId = requirement.researchId(owner)
        val target = entries[targetId] ?: error("Unknown research $targetId in $owner")
        requirement.task?.let { taskId ->
            val ids = target.taskLevels.map(ResearchTaskLevel::id) + target.taskDefinitions.map(ResearchTaskDefinition::id)
            require(taskId in ids) { "Unknown task ID $taskId in $targetId, referenced by $owner" }
        }
    }

    private fun resolveEntry(
        id: Identifier,
        categories: Map<Identifier, BookCategory>,
        entries: Map<Identifier, BookEntry>,
        result: LinkedHashMap<Identifier, ResolvedBookEntry>,
        visiting: MutableSet<Identifier>
    ): ResolvedBookEntry {
        result[id]?.let { return it }
        check(visiting.add(id)) { "Cyclic research dependency at $id" }
        val entry = entries.getValue(id)
        val dependencies = entry.dependencies.map { resolveEntry(it, categories, entries, result, visiting) }
        val category =
            entry.category ?: dependencies.lastOrNull()?.category
                ?: categories.values.minWith(compareBy<BookCategory> { it.order }.thenBy { it.id.toString() }).id
        val parent = dependencies.lastOrNull()
        val desired =
            entry.position
                ?: parent?.let { alignedPosition(entry, it) }
                ?: parent
                    ?.takeIf { entry.category == null }
                    ?.let { BookPosition(it.position.x, it.position.y + nodeHeight(it.entry) + NODE_GAP) }
        val position = findFree(category, desired, entry, result.values)
        val resolved = ResolvedBookEntry(entry, category, position)
        result[id] = resolved
        visiting.remove(id)
        return resolved
    }

    private fun alignedPosition(
        entry: BookEntry,
        parent: ResolvedBookEntry
    ): BookPosition? {
        val align = entry.align
        if (align.isEmpty()) return null
        if (BookEntryAlign.UP in align && BookEntryAlign.DOWN in align) return null
        if (BookEntryAlign.LEFT in align && BookEntryAlign.RIGHT in align) return null

        var x = parent.position.x
        var y = parent.position.y
        when {
            BookEntryAlign.LEFT in align -> x -= nodeWidth(entry) + NODE_GAP
            BookEntryAlign.RIGHT in align -> x += nodeWidth(parent.entry) + NODE_GAP
        }
        when {
            BookEntryAlign.UP in align -> y -= nodeHeight(entry) + NODE_GAP
            BookEntryAlign.DOWN in align -> y += nodeHeight(parent.entry) + NODE_GAP
        }
        return BookPosition(x, y)
    }

    private fun findFree(
        category: Identifier,
        desired: BookPosition?,
        entry: BookEntry,
        existing: Collection<ResolvedBookEntry>
    ): BookPosition {
        val occupied = existing.filter { it.category == category }
        if (desired != null && occupied.none { overlaps(desired, entry, it.position, it.entry) }) return desired
        var y = 48
        while (y < 4096) {
            var x = 32
            while (x < 1024) {
                val candidate = BookPosition(x, y)
                if (occupied.none { overlaps(candidate, entry, it.position, it.entry) }) return candidate
                x += 48
            }
            y += 48
        }
        error("Unable to place research ${entry.id}")
    }

    private fun overlaps(
        a: BookPosition,
        aEntry: BookEntry,
        b: BookPosition,
        bEntry: BookEntry
    ): Boolean {
        val padding = NODE_GAP
        return a.x < b.x + nodeWidth(bEntry) + padding &&
            a.x + nodeWidth(aEntry) + padding > b.x &&
            a.y < b.y + nodeHeight(bEntry) + padding &&
            a.y + nodeHeight(aEntry) + padding > b.y
    }

    private fun nodeWidth(entry: BookEntry) = entry.frame?.width ?: 18

    private fun nodeHeight(entry: BookEntry) = entry.frame?.height ?: 18

    private const val NODE_GAP = 14
}
