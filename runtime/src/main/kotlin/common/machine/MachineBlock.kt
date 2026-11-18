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

open class MachineBlock(
    properties: Properties,
    private val factory: (BlockPos, BlockState) -> BlockEntity,
    private val ticking: Ticking = Ticking.SERVER,
    private val dropsSelf: Boolean = false
): Block(properties), EntityBlock {
    enum class Ticking(val client: Boolean, val server: Boolean) {
        NONE(false, false), CLIENT(true, false), SERVER(false, true), BOTH(true, true)
    }

    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = factory(pos, state)

    override fun <T: BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? {
        if (!type.isValid(state)) return null
        return if (level.isClientSide) {
            if (!ticking.client) null
            else BlockEntityTicker { tickLevel, _, _, entity -> (entity as? Machine)?.clientTick(tickLevel) }
        } else {
            if (!ticking.server) null
            else BlockEntityTicker { tickLevel, _, _, entity -> if (tickLevel is ServerLevel) (entity as? Machine)?.serverTick(tickLevel) }
        }
    }

    override fun getDrops(state: BlockState, params: LootParams.Builder): List<ItemStack> =
        if (dropsSelf) listOf(ItemStack(this)) else super.getDrops(state, params)
}
