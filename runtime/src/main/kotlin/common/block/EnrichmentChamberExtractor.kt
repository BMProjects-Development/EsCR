package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.machine.MachineBlock
import com.algorithmlx.ecr.common.block.entity.enrichment.EnrichmentChamberExtractorEntity

class EnrichmentChamberExtractor(properties: Properties): MachineBlock(properties, ::EnrichmentChamberExtractorEntity, Ticking.NONE)
