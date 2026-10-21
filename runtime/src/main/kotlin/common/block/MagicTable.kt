package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.api.block.FullBlockParticles
import com.algorithmlx.ecr.api.utils.checkAndOpenMenu
import com.algorithmlx.ecr.api.utils.simpleTicker
import com.algorithmlx.ecr.common.block.entity.MagicTableBlockEntity
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

class MagicTable(properties: Properties): Block(properties), EntityBlock, FullBlockParticles {
    override fun newBlockEntity(
        worldPosition: BlockPos,
        blockState: BlockState
    ): BlockEntity = MagicTableBlockEntity(worldPosition, blockState)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        blockState: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T> = simpleTicker<T, MagicTableBlockEntity> { level, _, _, be -> MagicTableBlockEntity.onTick(level, be) }

    override fun getShape(state: BlockState, level: BlockGetter, pos: BlockPos, context: CollisionContext): VoxelShape = shape

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult = checkAndOpenMenu<MagicTableBlockEntity>(player, level, pos)

    private val shape by lazy {
        var shape = Shapes.empty()

        shape = Shapes.join(shape, Shapes.box(0.125, 0.0625, 0.125, 0.875, 0.375, 0.875), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.437, 0.373, 0.062, 0.563, 0.6235, 0.187), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.437, 0.375, 0.813, 0.563, 0.625, 0.939), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.814, 0.375, 0.437, 0.939, 0.625, 0.563), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.062, 0.375, 0.437, 0.187, 0.625, 0.563), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.156, 0.375, 0.156, 0.844, 0.4375, 0.219), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.156, 0.375, 0.781, 0.844, 0.4375, 0.844), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.156, 0.375, 0.219, 0.219, 0.4375, 0.781), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.781, 0.375, 0.219, 0.844, 0.4375, 0.781), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.156, 0.5625, 0.156, 0.844, 0.625, 0.844), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.194, 0.6, 0.195, 0.806, 0.756, 0.805), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.156, 0.75, 0.156, 0.844, 0.8125, 0.844), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.156, 0.0, 0.156, 0.844, 0.0625, 0.844), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.125, 0.625, 0.438, 0.1875, 0.6875, 0.5625), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.8125, 0.625, 0.438, 0.875, 0.6875, 0.5625), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.4375, 0.625, 0.125, 0.5625, 0.6875, 0.1875), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.4375, 0.625, 0.8125, 0.5625, 0.6875, 0.875), BooleanOp.OR)

        shape
    }

}
