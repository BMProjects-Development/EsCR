package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.machine.MachineBlock
import com.algorithmlx.ecr.common.block.entity.ColdDistillerEntity

class ColdDistiller(properties: Properties): MachineBlock(properties, ::ColdDistillerEntity)
