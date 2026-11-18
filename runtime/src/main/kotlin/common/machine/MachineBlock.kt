package com.algorithmlx.ecr.common.machine

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.loot.LootParams

open class MachineBlock(properties: Properties, private val factory: (BlockPos, BlockState) -> MachineEntity): Block(properties), EntityBlock {
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = factory(pos, state)

    override fun <T: BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? =
        if (level.isClientSide) null else BlockEntityTicker { tickLevel, _, _, entity -> (entity as? MachineEntity)?.serverTick(tickLevel as ServerLevel) }

    override fun getDrops(state: BlockState, params: LootParams.Builder): List<ItemStack> = listOf(ItemStack(this))
}
