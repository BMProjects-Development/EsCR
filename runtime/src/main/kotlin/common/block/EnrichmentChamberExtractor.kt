package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.block.entity.enrichment.EnrichmentChamberExtractorEntity
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class EnrichmentChamberExtractor(properties: Properties): Block(properties), EntityBlock {
    override fun newBlockEntity(
        worldPosition: BlockPos,
        blockState: BlockState
    ): BlockEntity = EnrichmentChamberExtractorEntity(worldPosition, blockState)
}
