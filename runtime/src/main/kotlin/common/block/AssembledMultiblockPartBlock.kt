package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockPartEntity
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblocks
import com.algorithmlx.ecr.api.block.FullBlockParticles
import com.algorithmlx.ecr.common.block.entity.AssembledMultiblockPartBlockEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.function.BiConsumer

class AssembledMultiblockPartBlock(
    properties: Properties,
) : Block(properties),
    EntityBlock,
    FullBlockParticles {
    init {
        registerDefaultState(
            stateDefinition
                .any()
                .setValue(FACING, Direction.NORTH)
                .setValue(CONTROLLER, false),
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, CONTROLLER)
    }

    override fun newBlockEntity(
        worldPosition: BlockPos,
        blockState: BlockState,
    ): BlockEntity = AssembledMultiblockPartBlockEntity(worldPosition, blockState)

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = AssembledMultiblocks.formedSelectionShape(level, pos) ?: Shapes.block()

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext,
    ): VoxelShape = AssembledMultiblocks.formedPartShape(level, pos) ?: Shapes.block()

    override fun <T : BlockEntity> getTicker(
        level: Level,
        blockState: BlockState,
        type: BlockEntityType<T>,
    ): BlockEntityTicker<T> =
        BlockEntityTicker { tickerLevel, pos, _, _ ->
            AssembledMultiblocks.tick(tickerLevel, pos)
        }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult,
    ): InteractionResult {
        val data =
            (level.getBlockEntity(pos) as? AssembledMultiblockPartEntity)?.assembledMultiblockData
                ?: return InteractionResult.PASS
        if (!level.isLoaded(data.controllerPos)) return InteractionResult.PASS

        val controllerState = level.getBlockState(data.controllerPos)
        if (data.controllerPos == pos || controllerState.block === this) return InteractionResult.PASS

        val controllerHit =
            BlockHitResult(
                hitResult.location.add(
                    (data.controllerPos.x - pos.x).toDouble(),
                    (data.controllerPos.y - pos.y).toDouble(),
                    (data.controllerPos.z - pos.z).toDouble(),
                ),
                hitResult.direction,
                data.controllerPos,
                hitResult.isInside,
            )
        return controllerState.useWithoutItem(level, player, controllerHit)
    }

    override fun playerWillDestroy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        player: Player,
    ): BlockState {
        val original =
            AssembledMultiblocks.disassemble(level, pos)
                ?: return super.playerWillDestroy(level, pos, state, player)
        if (original.state.block === this) return super.playerWillDestroy(level, pos, state, player)

        return original.state.block.playerWillDestroy(level, pos, original.state, player)
    }

    override fun playerDestroy(
        level: ServerLevel,
        player: ServerPlayer,
        pos: BlockPos,
        state: BlockState,
        blockEntity: BlockEntity?,
        tool: ItemStack,
    ) {
        val data = (blockEntity as? AssembledMultiblockPartEntity)?.assembledMultiblockData
        if (data == null || state.block === this) {
            super.playerDestroy(level, player, pos, state, blockEntity, tool)
            return
        }

        state.block.playerDestroy(
            level,
            player,
            pos,
            state,
            data.original.createBlockEntity(level),
            tool,
        )
    }

    override fun onExplosionHit(
        state: BlockState,
        level: ServerLevel,
        pos: BlockPos,
        explosion: Explosion,
        dropConsumer: BiConsumer<ItemStack, BlockPos>,
    ) {
        val restored =
            AssembledMultiblocks.disassemble(level, pos)?.state
                ?: level.getBlockState(pos).takeUnless { current -> current.block === this }

        if (restored == null || restored.block === this) {
            super.onExplosionHit(state, level, pos, explosion, dropConsumer)
            return
        }

        restored.onExplosionHit(level, pos, explosion, dropConsumer)
    }

    companion object {
        @JvmField
        val FACING = HorizontalDirectionalBlock.FACING

        @JvmField
        val CONTROLLER: BooleanProperty = BooleanProperty.create("controller")
    }
}
