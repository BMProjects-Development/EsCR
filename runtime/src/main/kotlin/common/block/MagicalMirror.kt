package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.api.utils.simpleTicker
import com.algorithmlx.ecr.common.inventory.MagicalMirrorEntity
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class MagicalMirror(properties: Properties) : Block(properties), EntityBlock {
    override fun newBlockEntity(worldPosition: BlockPos, blockState: BlockState): BlockEntity =
        MagicalMirrorEntity(worldPosition, blockState)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        blockState: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T>? {
        if (level.isClientSide || type !== BlockEntityTypeRegistry.magicalMirror.get()) return null
        return simpleTicker<T, MagicalMirrorEntity> { _, _, _, blockEntity -> blockEntity.serverTick() }
    }
}
