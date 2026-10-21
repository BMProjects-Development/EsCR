package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.inventory.MIMNodeEntity
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties

class MIMNode(properties: Properties, factory: (BlockPos, BlockState) -> MIMNodeEntity) : MIMBlock(properties, factory) {
    init { registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN)) }
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState = defaultBlockState().setValue(FACING, context.clickedFace.opposite)
    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) { builder.add(FACING) }
    override fun rotate(state: BlockState, rotation: Rotation): BlockState = state.setValue(FACING, rotation.rotate(state.getValue(FACING)))
    override fun mirror(state: BlockState, mirror: Mirror): BlockState = state.rotate(mirror.getRotation(state.getValue(FACING)))
    companion object { val FACING = BlockStateProperties.FACING }
}
