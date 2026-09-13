package com.algorithmlx.ecr.api.chunk

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import java.util.*

object ChunkLoadingManager {
    private val levels = WeakHashMap<ServerLevel, MutableMap<BlockPos, Set<Long>>>()

    fun update(level: ServerLevel, owner: BlockPos, radius: Int) {
        require(radius >= 0) { $$"Chunk loading radius must be >= 0, got $$radius. If you need to disable chunk loader, use ChunkLoadingManager$remove" }

        val ownerKey = owner.immutable()
        val desired = chunksAround(ownerKey, radius)

        val levelOwners = levels.getOrPut(level, ::HashMap)
        val current = levelOwners[ownerKey].orEmpty()

        if (current == desired) return

        for (packed in current) {
            if (packed !in desired)
                ChunkLoadingPlatform.instance.remove(level, owner, ChunkPos.unpack(packed))
        }

        levelOwners[ownerKey] = desired
    }

    fun remove(level: ServerLevel, owner: BlockPos) {
        val ownerKey = owner.immutable()
        val levelOwners = levels[level]

        levelOwners?.remove(ownerKey)

        if (levelOwners?.isEmpty() == true) levels.remove(level)

        ChunkLoadingPlatform.instance.removeAll(level, owner)
    }

    private fun chunksAround(owner: BlockPos, radius: Int): Set<Long> {
        val center = ChunkPos.containing(owner)
        val chunks = HashSet<Long>()

        for (x in center.x - radius .. center.x + radius) {
            for (z in center.z - radius .. center.z + radius) {
                chunks += ChunkPos.pack(x, z)
            }
        }

        return chunks
    }
}
