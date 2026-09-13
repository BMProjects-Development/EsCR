package com.algorithmlx.ecr.common.block

import net.minecraft.core.BlockPos
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class RayTowerBase(properties: Properties): Block(properties) {
    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = shape

    private val shape by lazy {
        var shape = Shapes.empty()
        shape = Shapes.join(shape, Shapes.box(0.0, 0.0, 0.0, 0.25, 1.0, 0.25), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.75, 0.0, 0.0, 1.0, 1.0, 0.25), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.0, 0.0, 0.75, 0.25, 1.0, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.75, 0.0, 0.75, 1.0, 1.0, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.25, 0.0, 0.25, 0.75, 1.0, 0.75), BooleanOp.OR)
        shape
    }
}
