package com.algorithmlx.ecr.common.assembled

import com.algorithmlx.ecr.api.geo.AnimationType
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import com.algorithmlx.ecr.api.multiblock.assembled.*
import com.algorithmlx.ecr.common.block.AssembledMultiblockPartBlock
import com.algorithmlx.ecr.registry.BlockRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.world.level.Level

object AssembledMultiblockRuntime {
    @JvmStatic
    fun assemble(
        level: Level,
        definition: AssembledMultiblockDefinition,
        controllerPos: BlockPos,
        facing: Direction
    ): AssemblyResult = AssembledMultiblocks.assemble(
        level,
        definition,
        controllerPos,
        facing
    ) { _, originalState, controller, partFacing ->
        val controllerBlock = originalState.block as? AssembledMultiblockControllerBlock
        if (controller && controllerBlock != null) {
            controllerBlock.assembledState(originalState, partFacing)
        } else {
            BlockRegistry.assembledMultiblockPart.get().defaultBlockState()
                .setValue(AssembledMultiblockPartBlock.CONTROLLER, controller)
                .setValue(AssembledMultiblockPartBlock.FACING, partFacing)
        }
    }

    @JvmStatic
    fun assemble(
        level: Level,
        definitionId: Identifier,
        controllerPos: BlockPos,
        facing: Direction
    ): AssemblyResult {
        val definition = MultiblockDefinitions.assembled(definitionId)
            ?: return AssemblyResult.Failure(AssemblyFailureReason.UNKNOWN_DEFINITION)
        return assemble(level, definition, controllerPos, facing)
    }

    @JvmStatic
    fun playAnimation(
        level: Level,
        partPos: BlockPos,
        animation: String,
        type: AnimationType = AnimationType.PLAY_ONCE
    ): Boolean = AssembledMultiblocks.playAnimation(level, partPos, animation, type)

    @JvmStatic
    fun stopAnimation(level: Level, partPos: BlockPos): Boolean =
        AssembledMultiblocks.stopAnimation(level, partPos)
}
