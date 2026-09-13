package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.api.block.FullBlockParticles
import com.algorithmlx.ecr.api.utils.checkAndOpenMenu
import com.algorithmlx.ecr.api.utils.simpleTicker
import com.algorithmlx.ecr.common.block.entity.RadiatingChamberEntity
import com.algorithmlx.ecr.registry.BlockCodecRegistry
import com.mojang.serialization.MapCodec
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

class RadiatingChamber(properties: Properties): Block(properties), EntityBlock, FullBlockParticles {
    override fun newBlockEntity(
        worldPosition: BlockPos,
        blockState: BlockState
    ): BlockEntity = RadiatingChamberEntity(worldPosition, blockState)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        blockState: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T> = simpleTicker<T, RadiatingChamberEntity> { level, pos, _, be ->
        RadiatingChamberEntity.onTick(level, pos, be)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult = checkAndOpenMenu<RadiatingChamberEntity>(player, level, pos)

    override fun codec(): MapCodec<out Block> = BlockCodecRegistry.instance.radiatingChamber
}
