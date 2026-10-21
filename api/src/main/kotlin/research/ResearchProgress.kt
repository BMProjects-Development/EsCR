package com.algorithmlx.ecr.api.research

import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import com.algorithmlx.ecr.api.research.content.BookCategory
import com.algorithmlx.ecr.api.research.content.BookElement
import com.algorithmlx.ecr.api.research.content.BookElementSpec
import com.algorithmlx.ecr.api.research.content.BookEntry
import com.algorithmlx.ecr.api.research.content.CraftingBookElement
import com.algorithmlx.ecr.api.research.content.GroupBookElement
import com.algorithmlx.ecr.api.research.content.ResearchRequirement
import com.algorithmlx.ecr.api.research.serializer.researchJson
import com.mojang.serialization.Codec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType
import java.util.UUID

data class PlayerResearchData(
    val unlocked: MutableSet<Identifier> = LinkedHashSet(),
    val opened: MutableSet<Identifier> = LinkedHashSet(),
    val bookmarks: MutableList<BookBookmark> = mutableListOf(),
    var bookLevel: Identifier? = null,
    var viewState: BookViewState = BookViewState(),
    val completedTaskLevels: MutableMap<Identifier, Int> = LinkedHashMap(),
    var lastSyncedProgress: Map<Identifier, List<ResearchTaskProgress>> = emptyMap()
)

data class BookViewState(
    val category: Identifier? = null,
    val entry: Identifier? = null,
    val spread: Int = 0,
    val panX: Float = 0F,
    val panY: Float = 0F,
    val zoom: Float = 1F,
    val pickerX: Int = -1,
    val pickerY: Int = -1
)

data class BookBookmark(val research: Identifier, val spread: Int, val color: Int)

class ResearchSavedData private constructor(
    private val players: MutableMap<UUID, PlayerResearchData>
) : SavedData() {
    constructor() : this(LinkedHashMap())

    fun player(uuid: UUID): PlayerResearchData = players.getOrPut(uuid, ::PlayerResearchData)

    private fun encode(): String = researchJson.encodeToString(
        ResearchStoreDto(players.mapKeys { it.key.toString() }.mapValues { it.value.toDto() })
    )

    companion object {
        private val CODEC: Codec<ResearchSavedData> = Codec.STRING.xmap(::decode, ResearchSavedData::encode)
        @JvmField val TYPE = SavedDataType("research_progress".ecRL, ::ResearchSavedData, CODEC, DataFixTypes.LEVEL)

        private fun decode(json: String): ResearchSavedData {
            if (json.isBlank()) return ResearchSavedData(LinkedHashMap())
            val root = researchJson.parseToJsonElement(json).jsonObject
            val store = if ("players" in root) researchJson.decodeFromJsonElement<ResearchStoreDto>(root)
            else ResearchStoreDto(researchJson.decodeFromJsonElement<Map<String, PlayerResearchDataDto>>(root))
            val players = store.players
                .mapKeysTo(LinkedHashMap()) { UUID.fromString(it.key) }
                .mapValuesTo(LinkedHashMap()) { it.value.toModel() }
            return ResearchSavedData(players)
        }
    }
}

@Serializable
private data class ResearchStoreDto(val players: Map<String, PlayerResearchDataDto> = emptyMap())

@Serializable
private data class PlayerResearchDataDto(
    val unlocked: Set<String> = emptySet(),
    val opened: Set<String> = emptySet(),
    val favorites: Map<String, Int> = emptyMap(),
    val bookmarks: List<BookBookmarkDto> = emptyList(),
    val bookLevel: String? = null,
    val viewState: BookViewStateDto = BookViewStateDto(),
    val completedTaskLevels: Map<String, Int> = emptyMap()
)

@Serializable
private data class BookViewStateDto(
    val category: String? = null,
    val entry: String? = null,
    val spread: Int = 0,
    val panX: Float = 0F,
    val panY: Float = 0F,
    val zoom: Float = 1F,
    val pickerX: Int = -1,
    val pickerY: Int = -1
)

@Serializable
private data class BookBookmarkDto(val research: String, val spread: Int = 0, val color: Int)

private fun PlayerResearchData.toDto() = PlayerResearchDataDto(
    unlocked = unlocked.mapTo(LinkedHashSet(), Identifier::toString),
    opened = opened.mapTo(LinkedHashSet(), Identifier::toString),
    bookmarks = bookmarks.map { BookBookmarkDto(it.research.toString(), it.spread, it.color) },
    bookLevel = bookLevel?.toString(),
    viewState = viewState.toDto(),
    completedTaskLevels = completedTaskLevels.mapKeys { it.key.toString() }
)

private fun PlayerResearchDataDto.toModel() = PlayerResearchData(
    unlocked = unlocked.mapTo(LinkedHashSet(), Identifier::parse),
    opened = opened.mapTo(LinkedHashSet(), Identifier::parse),
    bookmarks = (bookmarks.map { BookBookmark(Identifier.parse(it.research), it.spread.coerceAtLeast(0), it.color) } +
        favorites.map { (research, color) -> BookBookmark(Identifier.parse(research), (color ushr 24) and 0xFF, color) })
        .distinctBy { it.research to it.spread }
        .toMutableList(),
    bookLevel = bookLevel?.let(Identifier::parse),
    viewState = viewState.toModel(),
    completedTaskLevels = completedTaskLevels.mapKeysTo(LinkedHashMap()) { Identifier.parse(it.key) }
)

private fun BookViewState.toDto() = BookViewStateDto(category?.toString(), entry?.toString(), spread, panX, panY, zoom, pickerX, pickerY)
private fun BookViewStateDto.toModel() = BookViewState(
    category?.let(Identifier::parse),
    entry?.let(Identifier::parse),
    spread.coerceAtLeast(0),
    panX.takeIf { it.isFinite() } ?: 0F,
    panY.takeIf { it.isFinite() } ?: 0F,
    zoom.takeIf { it.isFinite() }?.coerceIn(0.5F, 2F) ?: 1F,
    pickerX,
    pickerY
)

object ResearchProgress {
    @JvmStatic
    fun data(player: ServerPlayer): PlayerResearchData = storage(player.level().server).player(player.uuid)

    @JvmStatic
    fun has(player: ServerPlayer, research: Identifier): Boolean = research in data(player).unlocked

    @JvmStatic
    fun available(player: ServerPlayer, entry: BookEntry): Boolean {
        val category = ResearchCatalog.snapshot().layout[entry.id]
            ?.category
            ?.let(ResearchCatalog.snapshot().categories::get)
            ?: return false
        return categoryAvailable(player, category) &&
            entry.dependencies.all { has(player, it) } &&
            entry.requirements.all { requirementMet(player, entry.id, it) }
    }

    @JvmStatic
    fun categoryAvailable(player: ServerPlayer, category: BookCategory): Boolean {
        val playerData = data(player)
        return category.dependencies.all { it in playerData.unlocked } && meetsBookLevel(playerData.bookLevel, category.bookLevel)
    }

    @JvmStatic
    fun setBookLevel(player: ServerPlayer, level: Identifier): Boolean {
        if (!ECRegistries.BOOK_TYPES.containsKey(level)) return false
        val playerData = data(player)
        playerData.bookLevel = level
        storage(player.level().server).setDirty()
        sync(player)
        return true
    }

    @JvmStatic
    fun updateView(player: ServerPlayer, state: BookViewState) {
        val playerData = data(player)
        val category = state.category?.takeIf { it in ResearchCatalog.snapshot().categories }
        val entry = state.entry?.takeIf { id ->
            ResearchCatalog.snapshot().entries[id]?.let { available(player, it) } == true
        }
        val openedChanged = entry?.let(playerData.opened::add) == true
        playerData.viewState = state.copy(
            category = category,
            entry = entry,
            spread = state.spread.coerceAtLeast(0),
            panX = state.panX.takeIf { it.isFinite() } ?: 0F,
            panY = state.panY.takeIf { it.isFinite() } ?: 0F,
            zoom = state.zoom.takeIf { it.isFinite() }?.coerceIn(0.5F, 2F) ?: 1F
        )
        storage(player.level().server).setDirty()
        if (openedChanged) if (unlockAutomatic(player)) sync(player) else syncProgress(player)
    }

    @JvmStatic
    fun tryUnlock(player: ServerPlayer, research: Identifier): Boolean {
        val entry = ResearchCatalog.snapshot().entries[research] ?: return false
        if (entry.automatic || entry.currentLevelOpenOnly(data(player))) return false
        val playerData = data(player)
        if (research in playerData.unlocked || !available(player, entry)) return false
        if (!advanceTaskLevel(player, playerData, entry)) return false
        storage(player.level().server).setDirty()
        unlockAutomatic(player)
        sync(player)
        return true
    }

    @JvmStatic
    fun unlockAutomatic(player: ServerPlayer): Boolean {
        val playerData = data(player)
        val advanced = HashSet<Identifier>()
        var changed: Boolean
        var anyChanged = false
        do {
            changed = false
            ResearchCatalog.snapshot().entries.values.forEach { entry ->
                if ((entry.automatic || entry.currentLevelOpenOnly(playerData)) &&
                    entry.id !in advanced && entry.id !in playerData.unlocked && available(player, entry) &&
                    advanceTaskLevel(player, playerData, entry)
                ) {
                    advanced += entry.id
                    changed = true
                    anyChanged = true
                }
            }
        } while (changed)
        if (anyChanged) storage(player.level().server).setDirty()
        return anyChanged
    }

    @JvmStatic
    fun setBookmark(player: ServerPlayer, research: Identifier, spread: Int, color: Int?) {
        val playerData = data(player)
        if (research !in playerData.unlocked) return
        val page = spread.coerceAtLeast(0)
        playerData.bookmarks.removeIf { it.research == research && it.spread == page }
        if (color != null) playerData.bookmarks += BookBookmark(research, page, color)
        storage(player.level().server).setDirty()
        sync(player)
    }

    @JvmStatic
    fun onPlayerJoin(player: ServerPlayer) {
        val playerData = data(player)
        if (playerData.bookLevel == null) {
            playerData.bookLevel = defaultBookLevel()
            storage(player.level().server).setDirty()
        }
        unlockAutomatic(player)
        sync(player)
    }

    @JvmStatic
    fun tick(player: ServerPlayer) {
        if (player.level().gameTime % 20L != 0L) return
        if (unlockAutomatic(player)) sync(player) else syncProgress(player)
    }

    @JvmStatic
    fun sync(player: ServerPlayer) {
        val playerData = data(player)
        val progress = collectProgress(player)
        playerData.lastSyncedProgress = progress
        ResearchNetwork.sendToPlayer(
            player,
            ResearchSyncPayload(
                ResearchCatalog.exportJson(),
                MultiblockDefinitions.jsonResources(),
                playerData.unlocked,
                playerData.bookmarks,
                progress,
                playerData.completedTaskLevels,
                playerData.bookLevel,
                collectBookRecipes(player.level().server),
                playerData.viewState
            )
        )
    }

    @JvmStatic
    fun syncProgress(player: ServerPlayer) {
        val playerData = data(player)
        val progress = collectProgress(player)
        if (progress == playerData.lastSyncedProgress) return
        playerData.lastSyncedProgress = progress
        ResearchNetwork.sendProgressToPlayer(player, ResearchProgressPayload(progress))
    }

    @JvmStatic
    fun syncAll(server: MinecraftServer) {
        server.playerList.players.forEach(::sync)
    }

    @JvmStatic
    fun reset(player: ServerPlayer) {
        val playerData = data(player)
        playerData.unlocked.clear()
        playerData.opened.clear()
        playerData.bookmarks.clear()
        playerData.completedTaskLevels.clear()
        playerData.lastSyncedProgress = emptyMap()
        playerData.bookLevel = defaultBookLevel()
        playerData.viewState = BookViewState()
        storage(player.level().server).setDirty()
        sync(player)
    }

    @JvmStatic
    fun grant(player: ServerPlayer, research: Identifier, taskId: String? = null): Boolean {
        val entry = ResearchCatalog.snapshot().entries[research] ?: return false
        val playerData = data(player)
        if (taskId == null) {
            playerData.unlocked += research
            playerData.completedTaskLevels.remove(research)
        } else {
            val levelIndex = entry.taskLevels.indexOfFirst { level ->
                level.id == taskId || level.tasks.any { it.id == taskId }
            }
            if (levelIndex < 0) return false
            if (levelIndex == entry.taskLevels.lastIndex) {
                playerData.unlocked += research
                playerData.completedTaskLevels.remove(research)
            }
            else
                playerData.completedTaskLevels[research] = maxOf(playerData.completedTaskLevels[research] ?: 0, levelIndex + 1)
        }
        storage(player.level().server).setDirty()
        unlockAutomatic(player)
        sync(player)
        return true
    }

    @JvmStatic
    fun grantAll(player: ServerPlayer) {
        val playerData = data(player)
        playerData.unlocked += ResearchCatalog.snapshot().entries.keys
        playerData.completedTaskLevels.clear()
        storage(player.level().server).setDirty()
        sync(player)
    }

    @JvmStatic
    fun requirementMet(player: ServerPlayer, owner: Identifier, requirement: ResearchRequirement): Boolean {
        val research = requirement.researchId(owner)
        val taskId = requirement.task ?: return has(player, research)
        val entry = ResearchCatalog.snapshot().entries[research] ?: return false
        if (has(player, research)) return entry.hasTask(taskId)
        val completedLevel = data(player).completedTaskLevels[research] ?: 0
        entry.taskLevels.forEachIndexed { levelIndex, level ->
            if (level.id == taskId) return levelIndex < completedLevel || level.tasks.all { it.task.progress(player, entry.id).complete }
            level.tasks.forEach { definition ->
                if (definition.id == taskId) return levelIndex < completedLevel || definition.task.progress(player, entry.id).complete
            }
        }
        return false
    }

    private fun storage(server: MinecraftServer): ResearchSavedData = server.overworld().dataStorage.computeIfAbsent(ResearchSavedData.TYPE)

    private fun defaultBookLevel(): Identifier? = ECRegistries.BOOK_TYPES.entrySet()
        .minByOrNull { it.value.order }
        ?.key
        ?.identifier()

    private fun collectProgress(player: ServerPlayer): Map<Identifier, List<ResearchTaskProgress>> =
        ResearchCatalog.snapshot().entries.values.associate { entry ->
            entry.id to entry.tasks.map { it.progress(player, entry.id) }
        }

    private fun collectBookRecipes(server: MinecraftServer): Map<Identifier, Recipe<*>> =
        ResearchCatalog.snapshot().entries.values.asSequence()
            .flatMap { entry -> entry.pages.asSequence() }
            .flatMap { page -> page.elements.asSequence() }
            .flatMap(BookElementSpec::walkElements)
            .mapNotNull { it as? CraftingBookElement }
            .map(CraftingBookElement::recipe)
            .distinct()
            .mapNotNull { id ->
                val key = ResourceKey.create(Registries.RECIPE, id)
                server.recipeManager.byKey(key).orElse(null)?.value()?.let { id to it }
            }
            .toMap(LinkedHashMap())

    private fun advanceTaskLevel(player: ServerPlayer, data: PlayerResearchData, entry: BookEntry): Boolean {
        if (entry.taskLevels.isEmpty()) {
            data.unlocked += entry.id
            data.completedTaskLevels.remove(entry.id)
            return true
        }
        val levelIndex = data.completedTaskLevels[entry.id]?.coerceIn(0, entry.taskLevels.lastIndex) ?: 0
        val level = entry.taskLevels[levelIndex]
        if (level.tasks.any { !it.task.progress(player, entry.id).complete }) return false
        level.tasks.forEach { it.task.consume(player) }
        val nextLevel = levelIndex + 1
        if (nextLevel >= entry.taskLevels.size) {
            data.unlocked += entry.id
            data.completedTaskLevels.remove(entry.id)
        }
        else
            data.completedTaskLevels[entry.id] = nextLevel
        return true
    }

    fun meetsBookLevel(current: Identifier?, required: Identifier?): Boolean {
        if (required == null) return true
        val requiredLevel = ECRegistries.BOOK_TYPES.getOptional(required).orElse(null) ?: return false
        val currentLevel = current?.let { ECRegistries.BOOK_TYPES.getOptional(it).orElse(null) } ?: return false
        return currentLevel.order >= requiredLevel.order || inheritsBookLevel(current, required, HashSet())
    }

    private fun inheritsBookLevel(current: Identifier, required: Identifier, visited: MutableSet<Identifier>): Boolean {
        if (current == required) return true
        if (!visited.add(current)) return false
        val level = ECRegistries.BOOK_TYPES.getOptional(current).orElse(null) ?: return false
        return level.inheritedTypes.any { inherited -> inheritsBookLevel(inherited.identifier(), required, visited) }
    }
}

private fun BookElementSpec.walkElements(): Sequence<BookElement> = sequence {
    yield(content)
    if (content is GroupBookElement) content.elements.forEach { child -> yieldAll(child.walkElements()) }
}

private fun BookEntry.hasTask(taskId: String): Boolean =
    taskLevels.any { it.id == taskId || it.tasks.any { definition -> definition.id == taskId } }

private fun BookEntry.currentLevelOpenOnly(data: PlayerResearchData): Boolean {
    if (taskLevels.isEmpty()) return false
    val levelIndex = data.completedTaskLevels[id]?.coerceIn(0, taskLevels.lastIndex) ?: 0
    val level = taskLevels.getOrNull(levelIndex) ?: return false
    return level.tasks.isNotEmpty() && level.tasks.all { it.task is OpenResearchTask }
}
