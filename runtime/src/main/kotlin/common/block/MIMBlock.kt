package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.api.menu.MenuTypePlatform
import com.algorithmlx.ecr.common.inventory.MIMDeviceEntity
import com.algorithmlx.ecr.common.menu.MIMMenuData
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

open class MIMBlock(
    properties: Properties,
    private val factory: (BlockPos, BlockState) -> MIMDeviceEntity
) : Block(properties), EntityBlock {
    override fun newBlockEntity(pos: BlockPos, state: BlockState): BlockEntity = factory(pos, state)
    override fun useWithoutItem(
        state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        val device = level.getBlockEntity(pos) as? MIMDeviceEntity ?: return InteractionResult.PASS
        if (player is ServerPlayer)
            MenuTypePlatform.instance.open(player, device, MIMMenuData.from(device), MIMMenuData.CODEC)
        return InteractionResult.SUCCESS
    }

    override fun <T : BlockEntity> getTicker(level: Level, state: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T>? =
        if (level.isClientSide) null else BlockEntityTicker { _, _, _, entity -> (entity as? MIMDeviceEntity)?.serverTick() }
}
