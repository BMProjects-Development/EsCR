package com.algorithmlx.ecr.fabric.chunk

import com.algorithmlx.ecr.api.chunk.ChunkLoadingPlatform
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import java.util.WeakHashMap

object FabricChunkLoadingPlatform: ChunkLoadingPlatform {
    private data class LevelState(
        val owners: MutableMap<BlockPos, MutableSet<Long>> = HashMap(),
        val references: MutableMap<Long, Int> = HashMap()
    )

    private val levels = WeakHashMap<ServerLevel, LevelState>()

    override fun add(level: ServerLevel, owner: BlockPos, chunk: ChunkPos): Boolean {
        val state = levels.getOrPut(level, ::LevelState)
        val ownerChunks = state.owners.getOrPut(owner.immutable(), ::HashSet)
        val packed = chunk.pack()

        if (!ownerChunks.add(packed)) return false

        val references = state.references[packed] ?: 0
        state.references[packed] = references + 1

        if (references == 0) level.setChunkForced(chunk.x, chunk.z, true)

        return true
    }

    override fun remove(level: ServerLevel, owner: BlockPos, chunk: ChunkPos): Boolean {
        val state = levels[level] ?: return false
        val ownerKey = owner.immutable()
        val ownerChunks = state.owners[ownerKey] ?: return false
        val packed = chunk.pack()

        if (!ownerChunks.remove(packed)) return false

        if (ownerChunks.isEmpty()) state.owners.remove(ownerKey)

        release(level, state, chunk, packed)
        cleanup(level, state)
        return true
    }

    override fun removeAll(level: ServerLevel, owner: BlockPos) {
        val state = levels[level] ?: return
        val ownerKey = owner.immutable()
        val chunks = state.owners.remove(ownerKey)?.toList() ?: return

        for (packed in chunks) {
            release(level, state, ChunkPos.unpack(packed), packed)
        }

        cleanup(level, state)
    }

    private fun release(level: ServerLevel, state: LevelState, chunk: ChunkPos, packed: Long) {
        val references = state.references[packed] ?: return

        if (references > 1) {
            state.references[packed] = references - 1
            return
        }

        state.references.remove(packed)
        level.setChunkForced(chunk.x, chunk.z, false)
    }

    private fun cleanup(level: ServerLevel, state: LevelState) {
        if (state.owners.isEmpty() && state.references.isEmpty()) levels.remove(level)
    }
}
