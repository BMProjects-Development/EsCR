package com.algorithmlx.ecr.api.multiblock.assembled

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import java.util.UUID

data class OriginalBlockSnapshot(
    val position: BlockPos,
    val state: BlockState,
    val blockEntityData: CompoundTag?
) {
    fun createBlockEntity(level: Level): BlockEntity? =
        blockEntityData?.let { data ->
            BlockEntity.loadStatic(position, state, data.copy(), level.registryAccess())
        }

    fun restore(level: Level, flags: Int = Block.UPDATE_ALL or Block.UPDATE_SUPPRESS_DROPS): Boolean {
        val stateChanged = level.setBlock(position, state, flags)
        if (!stateChanged && level.getBlockState(position) != state) return false

        createBlockEntity(level)?.let { blockEntity ->
            level.setBlockEntity(blockEntity)
            blockEntity.setChanged()
            level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS)
        }
        return true
    }

    companion object {
        @JvmStatic
        fun capture(level: Level, position: BlockPos): OriginalBlockSnapshot {
            val immutablePosition = position.immutable()
            val state = level.getBlockState(immutablePosition)
            val blockEntityData = level.getBlockEntity(immutablePosition)
                ?.saveWithFullMetadata(level.registryAccess())
            return OriginalBlockSnapshot(immutablePosition, state, blockEntityData)
        }
    }
}

data class AssembledMultiblockSnapshot(
    val instanceId: UUID,
    val definitionId: Identifier,
    val controllerPos: BlockPos,
    val facing: Direction,
    val parts: List<OriginalBlockSnapshot>
) {
    init {
        require(facing.axis.isHorizontal) { "Assembled multiblock facing must be horizontal" }
        require(parts.isNotEmpty()) { "Assembled multiblock snapshot must contain at least one part" }
        require(parts.any { part -> part.position == controllerPos }) {
            "Assembled multiblock snapshot must contain its controller"
        }
    }

    fun originalAt(position: BlockPos): OriginalBlockSnapshot? =
        parts.firstOrNull { part -> part.position == position }
}

data class AssembledMultiblockPartData(
    val instanceId: UUID,
    val definitionId: Identifier,
    val controllerPos: BlockPos,
    val facing: Direction,
    val original: OriginalBlockSnapshot,
    val fullSnapshot: AssembledMultiblockSnapshot?
)

interface AssembledMultiblockPartEntity {
    val assembledMultiblockData: AssembledMultiblockPartData?

    val isAssembledMultiblock: Boolean
        get() = assembledMultiblockData != null

    fun setAssembledMultiblockData(data: AssembledMultiblockPartData)

    fun clearAssembledMultiblockData()
}
