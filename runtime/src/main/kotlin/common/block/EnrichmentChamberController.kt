package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.api.multiblock.MultiblockPlacement
import com.algorithmlx.ecr.api.utils.checkAndOpenMenu
import com.algorithmlx.ecr.api.utils.simpleTicker
import com.algorithmlx.ecr.common.block.entity.enrichment.EnrichmentChamberControllerEntity
import com.algorithmlx.ecr.common.init.ECRTags
import com.algorithmlx.ecr.registry.BlockRegistry
import com.algorithmlx.ecr.registry.MultiblockRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.HorizontalDirectionalBlock
import net.minecraft.world.level.block.Mirror
import net.minecraft.world.level.block.Rotation
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.phys.BlockHitResult

class EnrichmentChamberController(properties: Properties): Block(properties), EntityBlock {
    init {
        this.registerDefaultState(
            this.stateDefinition.any().setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false)
        )
    }

    override fun newBlockEntity(
        worldPosition: BlockPos,
        blockState: BlockState
    ): BlockEntity = EnrichmentChamberControllerEntity(worldPosition, blockState)

    override fun <T : BlockEntity> getTicker(
        level: Level,
        blockState: BlockState,
        type: BlockEntityType<T>
    ): BlockEntityTicker<T> = simpleTicker<T, EnrichmentChamberControllerEntity> { level, _, state, blockEntity ->
        EnrichmentChamberControllerEntity.onTick(level, state, blockEntity)
    }

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult {
        return if (state.getValue(ACTIVE))
            checkAndOpenMenu<EnrichmentChamberControllerEntity>(player, level, pos)
        else super.useWithoutItem(state, level, pos, player, hitResult)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState =
        this.defaultBlockState().setValue(FACING, context.horizontalDirection.opposite)

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(FACING, ACTIVE)
    }

    override fun rotate(state: BlockState, rotation: Rotation): BlockState =
        state.setValue(FACING, rotation.rotate(state.getValue(FACING)))

    override fun mirror(state: BlockState, mirror: Mirror): BlockState =
        state.rotate(mirror.getRotation(state.getValue(FACING)))

    companion object {
        @JvmField val FACING = HorizontalDirectionalBlock.FACING
        @JvmField val ACTIVE = BooleanProperty.create("active")

        private const val MAX_STRUCTURE_SIZE = 127

        @JvmStatic
        fun findPlacement(level: Level, pos: BlockPos, state: BlockState): MultiblockPlacement? {
            val multiblock = MultiblockRegistry.instance.enrichmentChamber
            val facing = state.getValue(FACING)

            return listOf(facing.opposite, facing).firstNotNullOfOrNull { direction ->
                findControllerSurface(level, pos, direction)?.let { surface ->
                    multiblock.findPlacement(level, pos, direction) { pattern ->
                        if (
                            pattern.xSize == surface.size &&
                            pattern.ySize == surface.size &&
                            pattern.zSize == surface.size
                        ) {
                            sequenceOf(BlockPos(surface.startX, surface.startY, 0))
                        } else {
                            emptySequence()
                        }
                    }
                }
            }
        }

        private fun findControllerSurface(
            level: Level,
            pos: BlockPos,
            direction: Direction
        ): ControllerSurface? {
            val positiveHorizontal = direction.clockWise
            val negativeHorizontal = direction.counterClockWise
            val negativeDistance = frameDistance(level, pos, negativeHorizontal)
            val horizontalSize =
                negativeDistance + frameDistance(level, pos, positiveHorizontal) + 1
            val downDistance = frameDistance(level, pos, Direction.DOWN)
            val verticalSize = downDistance + frameDistance(level, pos, Direction.UP) + 1

            if (
                horizontalSize != verticalSize ||
                horizontalSize !in 5..MAX_STRUCTURE_SIZE ||
                horizontalSize % 2 == 0
            ) {
                return null
            }

            return ControllerSurface(horizontalSize, negativeDistance, downDistance)
        }

        private fun frameDistance(level: Level, pos: BlockPos, direction: Direction): Int {
            for (distance in 1..<MAX_STRUCTURE_SIZE) {
                if (!isFrameBlock(level.getBlockState(pos.relative(direction, distance)))) {
                    return distance - 1
                }
            }

            return MAX_STRUCTURE_SIZE - 1
        }

        private fun isFrameBlock(state: BlockState): Boolean =
            state.`is`(ECRTags.Blocks.ENRICHMENT_CHAMBER) ||
                state.`is`(BlockRegistry.instance.magicPlating) ||
                state.`is`(BlockRegistry.instance.enrichmentChamberController)

        private data class ControllerSurface(
            val size: Int,
            val startX: Int,
            val startY: Int
        )
    }
}
