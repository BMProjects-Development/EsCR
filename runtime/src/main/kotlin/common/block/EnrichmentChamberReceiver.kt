package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.machine.MachineBlock
import com.algorithmlx.ecr.common.block.entity.enrichment.EnrichmentChamberReceiverEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class EnrichmentChamberReceiver(properties: Properties): MachineBlock(properties, ::EnrichmentChamberReceiverEntity) {

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
