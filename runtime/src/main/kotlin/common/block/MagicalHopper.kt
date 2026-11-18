package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.block.entity.MagicalHopperEntity
import com.algorithmlx.ecr.common.machine.MachineBlock
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class MagicalHopper(properties: Properties): MachineBlock(properties, ::MagicalHopperEntity) {
    init { registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN)) }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) { builder.add(FACING) }
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState = defaultBlockState().setValue(FACING, context.clickedFace.opposite)
    override fun rotate(state: BlockState, rotation: Rotation): BlockState = state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
    override fun mirror(state: BlockState, mirror: Mirror): BlockState = rotate(state, mirror.getRotation(state.getValue(FACING)))

    companion object {
        @JvmField val FACING = BlockStateProperties.FACING
    }
}
