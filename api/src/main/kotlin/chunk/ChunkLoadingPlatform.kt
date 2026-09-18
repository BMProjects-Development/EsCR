package com.algorithmlx.ecr.api.chunk

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos

interface ChunkLoadingPlatform {
    fun add(level: ServerLevel, owner: BlockPos, chunk: ChunkPos): Boolean

    fun remove(level: ServerLevel, owner: BlockPos, chunk: ChunkPos): Boolean

    fun removeAll(level: ServerLevel, owner: BlockPos)

    companion object {
        lateinit var instance: ChunkLoadingPlatform
    }
}
