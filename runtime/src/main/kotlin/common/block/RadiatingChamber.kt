package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.machine.MachineBlock
import com.algorithmlx.ecr.api.block.FullBlockParticles
import com.algorithmlx.ecr.api.utils.checkAndOpenMenu
import com.algorithmlx.ecr.common.block.entity.RadiatingChamberEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.BlockHitResult

class RadiatingChamber(properties: Properties): MachineBlock(properties, ::RadiatingChamberEntity), FullBlockParticles {

    override fun useWithoutItem(
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hitResult: BlockHitResult
    ): InteractionResult = checkAndOpenMenu<RadiatingChamberEntity>(player, level, pos)

}
