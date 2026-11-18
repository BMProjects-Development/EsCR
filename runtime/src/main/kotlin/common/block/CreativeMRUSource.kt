package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.machine.MachineBlock
import com.algorithmlx.ecr.common.block.entity.CreativeMRUSourceEntity

class CreativeMRUSource(properties: Properties): MachineBlock(properties, ::CreativeMRUSourceEntity, Ticking.NONE)
