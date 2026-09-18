package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.api.block.FullBlockParticles
import com.algorithmlx.ecr.api.utils.checkAndOpenMenu
import com.algorithmlx.ecr.api.utils.simpleTicker
import com.algorithmlx.ecr.common.block.entity.MatrixDestructorEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

class MatrixDestructor(properties: Properties): Block(properties), EntityBlock, FullBlockParticles {
    override fun newBlockEntity(
        worldPosition: BlockPos,
        blockState: BlockState
    ): BlockEntity = MatrixDestructorEntity(worldPosition, blockState)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        blockState: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T> = simpleTicker<T, MatrixDestructorEntity> { level, _, _, v ->
        MatrixDestructorEntity.onTick(level, v)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult = checkAndOpenMenu<MatrixDestructorEntity>(player, level, pos)

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape =
        shape

    private val shape by lazy {
        var shape = Shapes.empty()
        shape = Shapes.join(shape, Shapes.box(0.0, 0.0, 0.0, 0.125, 0.9375, 0.125), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.0, 0.0, 0.875, 0.125, 0.9375, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.875, 0.0, 0.875, 1.0, 0.9375, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.875, 0.0, 0.0, 1.0, 0.9375, 0.125), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.0625, 0.0625, 0.0625, 0.9375, 0.5625, 0.9375), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.25, 0.5625, 0.25, 0.75, 0.625, 0.75), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.0, 0.5, 0.0, 1.0, 0.5625, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.0, 0.875, 0.0, 0.1875, 0.9375, 0.1875), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.8125, 0.875, 0.0, 1.0, 0.9375, 0.1875), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.0, 0.875, 0.8125, 0.1875, 0.9375, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.8125, 0.875, 0.8125, 1.0, 0.9375, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.0, 0.5625, 0.625, 0.125, 0.8125, 0.75), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.0, 0.5625, 0.25, 0.125, 0.8125, 0.375), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.25, 0.5625, 0.0, 0.375, 0.8125, 0.125), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.625, 0.5625, 0.0, 0.75, 0.8125, 0.125), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.875, 0.5625, 0.25, 1.0, 0.8125, 0.375), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.875, 0.5625, 0.625, 1.0, 0.8125, 0.75), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.625, 0.5625, 0.875, 0.75, 0.8125, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.25, 0.5625, 0.875, 0.375, 0.8125, 1.0), BooleanOp.OR)

        shape
    }
}
