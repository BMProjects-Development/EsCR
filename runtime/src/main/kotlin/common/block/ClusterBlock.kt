package com.algorithmlx.ecr.common.block

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

open class ClusterBlock(
    properties: Properties,
) : Block(properties) {
    override fun getShape(
        s: BlockState,
        l: BlockGetter,
        p: BlockPos,
        c: CollisionContext,
    ): VoxelShape =
        Shapes
            .rotateAll(boxZ(10.0, 16.0 - 7, 16.0))
            .getValue(Direction.DOWN.opposite)
}
