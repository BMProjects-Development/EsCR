package com.algorithmlx.ecr.api.utils

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.state.BlockState

inline fun <reified T> checkAndOpenMenu(
    player: Player,
    level: Level,
    blockPos: BlockPos
): InteractionResult where T : BlockEntity, T : MenuProvider {
    if (!level.isClientSide) {
        val be = level.getBlockEntity(blockPos)
        if (be is T) {
            player as ServerPlayer
            player.openMenuScreen(be, level, be.blockPos)
        } else if (be != null) {
            throw IllegalStateException("Can not open any block entity that is not instanceof ${T::class.java}")
        } else {
            return InteractionResult.FAIL
        }
    }

    return InteractionResult.SUCCESS
}

inline fun <T : BlockEntity, reified V : BlockEntity> simpleTicker(
    crossinline onTick: (level: Level, blockPos: BlockPos, blockState: BlockState, blockEntity: V) -> Unit
) = BlockEntityTicker<T> { level, pos, state, entity -> onTick(level, pos, state, entity as V) }

// INITIALIZED ON PLATFORM
lateinit var openMenuScreenInternal: (player: Player, provider: MenuProvider, level: Level, pos: BlockPos) -> Unit

fun Player.openMenuScreen(
    provider: MenuProvider,
    level: Level,
    pos: BlockPos
) = openMenuScreenInternal(this, provider, level, pos)
