package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.api.utils.simpleTicker
import com.algorithmlx.ecr.common.block.entity.enrichment.EnrichmentChamberReceiverEntity
import net.minecraft.core.BlockPos
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

class EnrichmentChamberReceiver(properties: Properties): Block(properties), EntityBlock {
    override fun newBlockEntity(
        worldPosition: BlockPos,
        blockState: BlockState
    ): BlockEntity = EnrichmentChamberReceiverEntity(worldPosition, blockState)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        blockState: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T> = simpleTicker<T, EnrichmentChamberReceiverEntity> { level, _, _, blockEntity ->
        EnrichmentChamberReceiverEntity.onTick(level, blockEntity)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        val blockEntity = level.getBlockEntity(pos)
        if (blockEntity is EnrichmentChamberReceiverEntity && blockEntity.hasController()) {
            if (!level.isClientSide) player.openMenu(blockEntity)

            return InteractionResult.SUCCESS
        }

        return super.useWithoutItem(state, level, pos, player, hitResult)
    }
}
