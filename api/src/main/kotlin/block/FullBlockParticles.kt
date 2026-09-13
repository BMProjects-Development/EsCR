package com.algorithmlx.ecr.api.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

/**
 * Is a "Label" for optimizing ParticleEngine.
 * A block with this class will spawn particles, with a Voxel Shape by default.
 * Necessary to reduce the number of lags when destroying a block with a changed Voxel Shape.
 */
interface FullBlockParticles {
    fun isEnableForPart(level: Level, blockPos: BlockPos, state: BlockState): Boolean = true
}
