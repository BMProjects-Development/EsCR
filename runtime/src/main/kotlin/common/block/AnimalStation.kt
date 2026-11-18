package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.machine.AnimalStationLogic
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.redstone.Orientation
import net.minecraft.world.level.storage.loot.LootParams

class AnimalStation(properties: Properties, private val operation: Operation): Block(properties) {
    init { registerDefaultState(stateDefinition.any().setValue(POWERED, false)) }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) { builder.add(POWERED) }
    override fun getDrops(state: BlockState, params: LootParams.Builder): List<ItemStack> = listOf(ItemStack(this))

    override fun neighborChanged(state: BlockState, level: Level, pos: BlockPos, neighbor: Block, orientation: Orientation?, movedByPiston: Boolean) {
        updatePower(state, level, pos)
    }

    override fun onPlace(state: BlockState, level: Level, pos: BlockPos, oldState: BlockState, movedByPiston: Boolean) {
        if (!oldState.`is`(this)) updatePower(state, level, pos)
    }

    private fun updatePower(state: BlockState, level: Level, pos: BlockPos) {
        if (level !is ServerLevel) return
        val powered = level.hasNeighborSignal(pos)
        if (powered == state.getValue(POWERED)) return
        level.setBlock(pos, state.setValue(POWERED, powered), UPDATE_CLIENTS)
        if (!powered) return
        when (operation) {
            Operation.BREED -> AnimalStationLogic.breed(level, pos)
            Operation.SHEAR -> AnimalStationLogic.shear(level, pos)
        }
    }

    enum class Operation { BREED, SHEAR }

    companion object {
        @JvmField val POWERED = BlockStateProperties.POWERED
    }
}
