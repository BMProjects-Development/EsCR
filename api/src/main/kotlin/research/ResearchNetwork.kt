package com.algorithmlx.ecr.api.research

import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.api.research.content.BookCategory
import com.algorithmlx.ecr.api.research.content.BookEntry
import com.algorithmlx.ecr.api.research.content.ResearchRequirement
import com.algorithmlx.ecr.api.research.content.ResearchTaskDefinition
import com.algorithmlx.ecr.api.multiblock.MultiblockJsonResources
import com.algorithmlx.ecr.api.multiblock.MultiblockJsonSync
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.crafting.Recipe
import com.algorithmlx.ecr.api.network.Network
import com.algorithmlx.ecr.api.network.sendToServer

data class ResearchSyncPayload(
    val catalog: String,
    val multiblockDefinitions: MultiblockJsonResources,
    val unlocked: Set<Identifier>,
    val bookmarks: List<BookBookmark>,
    val taskProgress: Map<Identifier, List<ResearchTaskProgress>>,
    val completedTaskLevels: Map<Identifier, Int>,
    val bookLevel: Identifier?,
    val recipes: Map<Identifier, Recipe<*>>,
    val viewState: BookViewState
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<ResearchSyncPayload>("research_sync".ecRL)
        @JvmField val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, ResearchSyncPayload> = StreamCodec.of(
            { buffer, value ->
                buffer.writeUtf(value.catalog, MAX_CATALOG_SIZE)
                MultiblockJsonResources.STREAM_CODEC.encode(buffer, value.multiblockDefinitions)
                buffer.writeVarInt(value.unlocked.size)
                value.unlocked.forEach(buffer::writeIdentifier)
                buffer.writeVarInt(value.bookmarks.size)
                value.bookmarks.forEach { bookmark ->
                    buffer.writeIdentifier(bookmark.research)
                    buffer.writeVarInt(bookmark.spread)
                    buffer.writeInt(bookmark.color)
                }
                buffer.writeVarInt(value.taskProgress.size)
                value.taskProgress.forEach { (id, progress) ->
                    buffer.writeIdentifier(id)
                    buffer.writeVarInt(progress.size)
                    progress.forEach {
                        buffer.writeVarInt(it.current)
                        buffer.writeVarInt(it.required)
                    }
                }
                buffer.writeVarInt(value.completedTaskLevels.size)
                value.completedTaskLevels.forEach { (id, level) ->
                    buffer.writeIdentifier(id)
                    buffer.writeVarInt(level)
                }
                buffer.writeBoolean(value.bookLevel != null)
                value.bookLevel?.let(buffer::writeIdentifier)
                buffer.writeVarInt(value.recipes.size)
                value.recipes.forEach { (id, recipe) ->
                    buffer.writeIdentifier(id)
                    Recipe.STREAM_CODEC.encode(buffer, recipe)
                }
                buffer.writeViewState(value.viewState)
            },
            { buffer ->
                val catalog = buffer.readUtf(MAX_CATALOG_SIZE)
                val multiblockDefinitions = MultiblockJsonResources.STREAM_CODEC.decode(buffer)
                val unlocked = LinkedHashSet<Identifier>().apply {
                    repeat(buffer.readVarInt()) { add(buffer.readIdentifier()) }
                }
                val bookmarks = List(buffer.readVarInt()) {
                    BookBookmark(buffer.readIdentifier(), buffer.readVarInt(), buffer.readInt())
                }
                val progress = LinkedHashMap<Identifier, List<ResearchTaskProgress>>().apply {
                    repeat(buffer.readVarInt()) {
                        val id = buffer.readIdentifier()
                        put(id, List(buffer.readVarInt()) { ResearchTaskProgress(buffer.readVarInt(), buffer.readVarInt()) })
                    }
                }
                val completedTaskLevels = LinkedHashMap<Identifier, Int>().apply {
                    repeat(buffer.readVarInt()) { put(buffer.readIdentifier(), buffer.readVarInt()) }
                }
                val bookLevel = if (buffer.readBoolean()) buffer.readIdentifier() else null
                val recipes = LinkedHashMap<Identifier, Recipe<*>>().apply {
                    repeat(buffer.readVarInt()) { put(buffer.readIdentifier(), Recipe.STREAM_CODEC.decode(buffer)) }
                }
                ResearchSyncPayload(
                    catalog,
                    multiblockDefinitions,
                    unlocked,
                    bookmarks,
                    progress,
                    completedTaskLevels,
                    bookLevel,
                    recipes,
                    buffer.readViewState()
                )
            }
        )
        private const val MAX_CATALOG_SIZE = 8 * 1024 * 1024
    }
}

data class ResearchProgressPayload(
    val taskProgress: Map<Identifier, List<ResearchTaskProgress>>
) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<ResearchProgressPayload>("research_progress".ecRL)
        @JvmField val STREAM_CODEC: StreamCodec<FriendlyByteBuf, ResearchProgressPayload> = StreamCodec.of(
            { buffer, value -> buffer.writeTaskProgress(value.taskProgress) },
            { buffer -> ResearchProgressPayload(buffer.readTaskProgress()) }
        )
    }
}

data class UpdateBookViewPayload(val state: BookViewState) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<UpdateBookViewPayload>("update_book_view".ecRL)
        @JvmField val STREAM_CODEC: StreamCodec<FriendlyByteBuf, UpdateBookViewPayload> = StreamCodec.of(
            { buffer, value -> buffer.writeViewState(value.state) },
            { buffer -> UpdateBookViewPayload(buffer.readViewState()) }
        )
    }
}

data class CompleteResearchPayload(val research: Identifier) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<CompleteResearchPayload>("complete_research".ecRL)
        @JvmField val STREAM_CODEC: StreamCodec<FriendlyByteBuf, CompleteResearchPayload> = StreamCodec.of(
            { buffer, value -> buffer.writeIdentifier(value.research) },
            { buffer -> CompleteResearchPayload(buffer.readIdentifier()) }
        )
    }
}

data class FavoriteResearchPayload(val research: Identifier, val spread: Int, val color: Int?) : CustomPacketPayload {
    override fun type() = TYPE

    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<FavoriteResearchPayload>("favorite_research".ecRL)
        @JvmField val STREAM_CODEC: StreamCodec<FriendlyByteBuf, FavoriteResearchPayload> = StreamCodec.of(
            { buffer, value ->
                buffer.writeIdentifier(value.research)
                buffer.writeVarInt(value.spread)
                buffer.writeBoolean(value.color != null)
                value.color?.let(buffer::writeInt)
            },
            { buffer -> FavoriteResearchPayload(buffer.readIdentifier(), buffer.readVarInt(), if (buffer.readBoolean()) buffer.readInt() else null) }
        )
    }
}

object ResearchNetwork {
    @JvmStatic fun sendToPlayer(player: ServerPlayer, payload: ResearchSyncPayload) = Network.sendTo(player, payload)
    @JvmStatic fun sendProgressToPlayer(player: ServerPlayer, payload: ResearchProgressPayload) = Network.sendTo(player, payload)
    @JvmStatic fun completeResearch(research: Identifier) = CompleteResearchPayload(research).sendToServer()
    @JvmStatic fun updateFavorite(research: Identifier, spread: Int, color: Int?) =
        FavoriteResearchPayload(research, spread, color).sendToServer()
    @JvmStatic fun updateView(state: BookViewState) = runCatching { UpdateBookViewPayload(state).sendToServer() }
    @JvmField var researchUnlocked: (Identifier) -> Unit = {}
    @JvmField var taskCompleted: (Identifier, ResearchTaskDefinition) -> Unit = { _, _ -> }
}

object ClientResearchState {
    @Volatile private var unlockedResearch = emptySet<Identifier>()
    @Volatile private var bookmarks = emptyList<BookBookmark>()
    @Volatile private var progress = emptyMap<Identifier, List<ResearchTaskProgress>>()
    @Volatile private var completedTaskLevels = emptyMap<Identifier, Int>()
    @Volatile private var currentBookLevel: Identifier? = null
    @Volatile private var recipes = emptyMap<Identifier, Recipe<*>>()
    @Volatile private var savedViewState = BookViewState()
    @Volatile private var stateRevision = 0L

    @JvmStatic fun apply(payload: ResearchSyncPayload) {
        val previous = unlockedResearch
        val previousProgress = progress
        MultiblockJsonSync.apply(payload.multiblockDefinitions)
        ResearchCatalog.importJson(payload.catalog)
        val nextUnlocked = payload.unlocked.toSet()
        val added = if (stateRevision == 0L && previous.isEmpty()) emptySet() else nextUnlocked - previous
        unlockedResearch = nextUnlocked
        bookmarks = payload.bookmarks.toList()
        progress = payload.taskProgress.toMap()
        completedTaskLevels = payload.completedTaskLevels.toMap()
        currentBookLevel = payload.bookLevel
        recipes = payload.recipes.toMap()
        savedViewState = payload.viewState
        notifyCompletedTasks(previousProgress, progress)
        stateRevision++
        added.forEach(ResearchNetwork.researchUnlocked)
    }

    @JvmStatic fun apply(payload: ResearchProgressPayload) {
        val previousProgress = progress
        progress = payload.taskProgress.toMap()
        notifyCompletedTasks(previousProgress, progress)
        stateRevision++
    }

    @JvmStatic fun has(research: Identifier): Boolean = research in unlockedResearch
    @JvmStatic fun unlocked(): Set<Identifier> = unlockedResearch
    @JvmStatic fun bookmarks(): List<BookBookmark> = bookmarks
    @JvmStatic fun bookmark(research: Identifier, spread: Int): BookBookmark? =
        bookmarks.firstOrNull { it.research == research && it.spread == spread }
    @JvmStatic fun taskProgress(research: Identifier): List<ResearchTaskProgress> = progress[research].orEmpty()
    @JvmStatic fun completedTaskLevels(research: Identifier): Int = completedTaskLevels[research] ?: 0
    @JvmStatic fun taskComplete(research: Identifier, taskId: String): Boolean {
        val entry = ResearchCatalog.snapshot().entries[research] ?: return false
        if (has(research)) return entry.taskLevels.any { level ->
            level.id == taskId || level.tasks.any { it.id == taskId }
        }
        entry.taskLevels.forEachIndexed { levelIndex, level ->
            if (level.id == taskId) return levelIndex < completedTaskLevels(research)
        }
        var flatIndex = 0
        entry.taskLevels.forEachIndexed { levelIndex, level ->
            level.tasks.forEach { definition ->
                if (definition.id == taskId) {
                    if (levelIndex < completedTaskLevels(research)) return true
                    if (levelIndex > completedTaskLevels(research)) return false
                    return progress[research]?.getOrNull(flatIndex)?.complete == true
                }
                flatIndex++
            }
        }
        return false
    }
    @JvmStatic fun requirementMet(owner: Identifier, requirement: ResearchRequirement): Boolean {
        val research = requirement.researchId(owner)
        return requirement.task?.let { taskComplete(research, it) } ?: has(research)
    }
    @JvmStatic fun bookLevel(): Identifier? = currentBookLevel
    @JvmStatic fun recipe(id: Identifier): Recipe<*>? = recipes[id]
    @JvmStatic fun viewState(): BookViewState = savedViewState
    @JvmStatic fun revision(): Long = stateRevision
    @JvmStatic fun updateLocalView(state: BookViewState) {
        savedViewState = state
    }
    @JvmStatic fun categoryAvailable(category: BookCategory): Boolean =
        category.dependencies.all(::has) && ResearchProgress.meetsBookLevel(currentBookLevel, category.bookLevel)

    @JvmStatic fun entryAvailable(entry: BookEntry): Boolean {
        val category = ResearchCatalog.snapshot().layout[entry.id]
            ?.category
            ?.let(ResearchCatalog.snapshot().categories::get)
            ?: return false
        return categoryAvailable(category) && entry.dependencies.all(::has) &&
            entry.requirements.all { requirementMet(entry.id, it) }
    }

    private fun notifyCompletedTasks(
        previous: Map<Identifier, List<ResearchTaskProgress>>,
        next: Map<Identifier, List<ResearchTaskProgress>>
    ) {
        if (stateRevision == 0L && previous.isEmpty()) return
        ResearchCatalog.snapshot().entries.values.forEach { entry ->
            val before = previous[entry.id].orEmpty()
            val after = next[entry.id].orEmpty()
            entry.taskDefinitions.forEachIndexed { index, definition ->
                val old = before.getOrNull(index)
                val current = after.getOrNull(index) ?: return@forEachIndexed
                if (old?.complete != true && current.complete) ResearchNetwork.taskCompleted(entry.id, definition)
            }
        }
    }
}

private fun FriendlyByteBuf.writeTaskProgress(progress: Map<Identifier, List<ResearchTaskProgress>>) {
    writeVarInt(progress.size)
    progress.forEach { (id, tasks) ->
        writeIdentifier(id)
        writeVarInt(tasks.size)
        tasks.forEach {
            writeVarInt(it.current)
            writeVarInt(it.required)
        }
    }
}

private fun FriendlyByteBuf.readTaskProgress(): Map<Identifier, List<ResearchTaskProgress>> =
    LinkedHashMap<Identifier, List<ResearchTaskProgress>>().apply {
        repeat(readVarInt()) {
            put(readIdentifier(), List(readVarInt()) { ResearchTaskProgress(readVarInt(), readVarInt()) })
        }
    }

private fun FriendlyByteBuf.writeViewState(state: BookViewState) {
    writeBoolean(state.category != null)
    state.category?.let(::writeIdentifier)
    writeBoolean(state.entry != null)
    state.entry?.let(::writeIdentifier)
    writeVarInt(state.spread.coerceAtLeast(0))
    writeFloat(state.panX)
    writeFloat(state.panY)
    writeFloat(state.zoom)
    writeVarInt(state.pickerX + 1)
    writeVarInt(state.pickerY + 1)
}

private fun FriendlyByteBuf.readViewState() = BookViewState(
    if (readBoolean()) readIdentifier() else null,
    if (readBoolean()) readIdentifier() else null,
    readVarInt(),
    readFloat(),
    readFloat(),
    readFloat(),
    readVarInt() - 1,
    readVarInt() - 1
)
