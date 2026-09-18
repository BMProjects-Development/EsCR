package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockControllerBlock
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblocks
import com.algorithmlx.ecr.api.block.FullBlockParticles
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import com.algorithmlx.ecr.api.utils.checkAndOpenMenu
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.api.utils.simpleTicker
import com.algorithmlx.ecr.common.block.entity.RayTowerEntity
import com.algorithmlx.ecr.common.init.ECRModIDs
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.Explosion
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.RenderShape
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.function.BiConsumer

class RayTower(properties: Properties): Block(properties), EntityBlock, AssembledMultiblockControllerBlock, FullBlockParticles {
    init {
        registerDefaultState(stateDefinition.any().setValue(ASSEMBLED, false))
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(ASSEMBLED)
    }

    override fun assembledState(originalState: BlockState, facing: Direction): BlockState =
        originalState.setValue(ASSEMBLED, true)

    override fun newBlockEntity(
        worldPosition: BlockPos,
        blockState: BlockState
    ): BlockEntity = RayTowerEntity(worldPosition, blockState)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        blockState: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T> = simpleTicker<T, RayTowerEntity> { level, blockPos, _, blockEntity ->
        RayTowerEntity.onTick(level, blockPos, blockEntity)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (!state.getValue(ASSEMBLED)) return InteractionResult.FAIL
        return checkAndOpenMenu<RayTowerEntity>(player, level, pos)
    }

    override fun getRenderShape(state: BlockState): RenderShape =
        if (state.getValue(ASSEMBLED) && hasFormedModel()) RenderShape.INVISIBLE else super.getRenderShape(state)

    override fun getOcclusionShape(state: BlockState): VoxelShape =
        if (state.getValue(ASSEMBLED)) Shapes.empty() else super.getOcclusionShape(state)

    override fun getShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = if (state.getValue(ASSEMBLED)) {
        AssembledMultiblocks.formedSelectionShape(level, pos) ?: Shapes.block()
    } else shape

    override fun getCollisionShape(
        state: BlockState,
        level: BlockGetter,
        pos: BlockPos,
        context: CollisionContext
    ): VoxelShape = if (state.getValue(ASSEMBLED)) {
        AssembledMultiblocks.formedPartShape(level, pos) ?: Shapes.block()
    } else {
        super.getCollisionShape(state, level, pos, context)
    }

    override fun playerWillDestroy(
        level: Level,
        pos: BlockPos,
        state: BlockState,
        player: Player
    ): BlockState {
        if (!state.getValue(ASSEMBLED)) return super.playerWillDestroy(level, pos, state, player)
        val original = AssembledMultiblocks.disassemble(level, pos)
            ?: return super.playerWillDestroy(level, pos, state, player)
        return original.state.block.playerWillDestroy(level, pos, original.state, player)
    }

    override fun onExplosionHit(
        state: BlockState,
        level: ServerLevel,
        pos: BlockPos,
        explosion: Explosion,
        dropConsumer: BiConsumer<ItemStack, BlockPos>
    ) {
        if (state.getValue(ASSEMBLED)) {
            val restored = AssembledMultiblocks.disassemble(level, pos)?.state
            if (restored != null) {
                restored.onExplosionHit(level, pos, explosion, dropConsumer)
                return
            }
        }
        super.onExplosionHit(state, level, pos, explosion, dropConsumer)
    }

    private fun hasFormedModel(): Boolean = MultiblockDefinitions
        .assembled(ECRModIDs.RAY_TOWER.ecRL)
        ?.formedModel != null

    private val shape by lazy {
        var shape = Shapes.empty()
        shape = Shapes.join(shape, Shapes.box(0.0, 0.0, 0.75, 0.25, 0.5, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.75, 0.0, 0.75, 1.0, 0.5, 1.0), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.75, 0.0, 0.0, 1.0, 0.5, 0.25), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.0, 0.0, 0.0, 0.25, 0.5, 0.25), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.25, 0.0, 0.25, 0.75, 0.5, 0.75), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.1875, 0.4375, 0.1875, 0.8125, 0.5625, 0.8125), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.0625, 0.5, 0.0625, 0.25, 0.9375, 0.25), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.0625, 0.5, 0.75, 0.25, 0.9375, 0.9375), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.75, 0.5, 0.75, 0.9375, 0.9375, 0.9375), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.75, 0.5, 0.0625, 0.9375, 0.9375, 0.25), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.1875, 0.875, 0.1875, 0.8125, 1.0, 0.3125), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.1875, 0.875, 0.6875, 0.8125, 1.0, 0.8125), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.1875, 0.875, 0.3125, 0.3125, 1.0, 0.6875), BooleanOp.OR)
        shape = Shapes.join(shape, Shapes.box(0.6875, 0.875, 0.3125, 0.8125, 1.0, 0.6875), BooleanOp.OR)

        shape
    }

    companion object {
        @JvmField
        val ASSEMBLED: BooleanProperty = BooleanProperty.create("assembled")
    }
}
