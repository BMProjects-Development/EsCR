package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.machine.MachineBlock
import com.algorithmlx.ecr.api.utils.checkAndOpenMenu
import com.algorithmlx.ecr.common.block.entity.MagicalTeleporterEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.phys.BlockHitResult

class MagicalTeleporter(properties: Properties): MachineBlock(properties, ::MagicalTeleporterEntity, Ticking.BOTH) {
    init {
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH))
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult = if (MagicalTeleporterEntity.hasValidStructure(level, pos))
        checkAndOpenMenu<MagicalTeleporterEntity>(player, level, pos)
    else
        InteractionResult.FAIL

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState = this.defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING)
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState = state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState = state.rotate(mirror.getRotation(state.getValue(FACING)))

    companion object {
        private val FACING = HorizontalDirectionalBlock.FACING
    }
}
