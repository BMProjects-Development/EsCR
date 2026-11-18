package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.machine.MachineBlock
import com.algorithmlx.ecr.common.block.entity.SunRayAbsorberEntity

class SunRayAbsorber(properties: Properties): MachineBlock(properties, ::SunRayAbsorberEntity)
