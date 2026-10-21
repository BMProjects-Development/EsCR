package com.algorithmlx.ecr.api.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState

interface FullBlockParticles {
    fun isEnableForPart(level: Level, blockPos: BlockPos, state: BlockState): Boolean = true
}
