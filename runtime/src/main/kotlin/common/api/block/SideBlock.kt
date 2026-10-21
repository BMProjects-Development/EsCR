package com.algorithmlx.ecr.common.api.block

import com.algorithmlx.ecr.api.block.FullBlockParticles
import com.algorithmlx.ecr.common.block.SolarPrism
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.sounds.SoundEvent
import net.minecraft.util.RandomSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.ScheduledTickAccess
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.CrossCollisionBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.material.Fluid
import net.minecraft.world.level.material.FluidState
import net.minecraft.world.level.material.Fluids
import java.util.Optional

abstract class SideBlock(properties: Properties): CrossCollisionBlock(
    8f, 8f, 16f, 16f, 16f,
    properties
), FullBlockParticles {
    init {
        this.registerDefaultState(
            this.stateDefinition.any().setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(WATERLOGGED, false)
        )
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(NORTH, EAST, WEST, SOUTH, WATERLOGGED)
    }

    override fun propagatesSkylightDown(state: BlockState): Boolean = state.fluidState.isEmpty

    override fun canPlaceLiquid(
        user: LivingEntity?,
        level: BlockGetter,
        pos: BlockPos,
        state: BlockState,
        type: Fluid
    ): Boolean = false

    override fun getFluidState(state: BlockState): FluidState = Fluids.EMPTY.defaultFluidState()

    override fun placeLiquid(level: LevelAccessor, pos: BlockPos, state: BlockState, fluidState: FluidState): Boolean = false

    override fun pickupBlock(user: LivingEntity?, level: LevelAccessor, pos: BlockPos, state: BlockState): ItemStack = ItemStack.EMPTY

    override fun getPickupSound(): Optional<SoundEvent> = Optional.empty()

    override fun updateShape(
        state: BlockState,
        level: LevelReader,
        ticks: ScheduledTickAccess,
        pos: BlockPos,
        directionToNeighbour: Direction,
        neighbourPos: BlockPos,
        neighbourState: BlockState,
        random: RandomSource
    ): BlockState {
        if (directionToNeighbour.axis.isHorizontal) {
            val newValue = attach(neighbourState)
            return state.setValue(PROPERTY_BY_DIRECTION[directionToNeighbour]!!, newValue)
        }

        return super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random)
    }

    override fun skipRendering(state: BlockState, adjacentState: BlockState, direction: Direction): Boolean {
        if (adjacentState.`is`(this)) {
            if (!direction.axis.isHorizontal) return true

            if (state.getValue(PROPERTY_BY_DIRECTION[direction]!!) && adjacentState.getValue(PROPERTY_BY_DIRECTION[direction.opposite]!!)) return true
        }

        return super.skipRendering(state, adjacentState, direction)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        val blockGetter: BlockGetter = context.level
        val blockPos = context.clickedPos
        val north = blockPos.north()
        val south = blockPos.south()
        val west = blockPos.west()
        val east = blockPos.east()
        val northState = blockGetter.getBlockState(north)
        val southState = blockGetter.getBlockState(south)
        val westState = blockGetter.getBlockState(west)
        val eastState = blockGetter.getBlockState(east)

        return this.defaultBlockState()
            .setValue(NORTH, attach(northState))
            .setValue(SOUTH, attach(southState))
            .setValue(WEST, attach(westState))
            .setValue(EAST, attach(eastState))
    }

    companion object {
        @JvmStatic
        fun attach(state: BlockState) = state.block is SolarPrism
    }
}
