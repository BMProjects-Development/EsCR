package com.algorithmlx.ecr.api.multiblock.assembled

import net.minecraft.core.Direction
import net.minecraft.world.level.block.state.BlockState

interface AssembledMultiblockControllerBlock {
    fun assembledState(originalState: BlockState, facing: Direction): BlockState
}
