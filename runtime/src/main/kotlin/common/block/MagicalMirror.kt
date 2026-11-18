package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.machine.MachineBlock
import com.algorithmlx.ecr.common.inventory.MagicalMirrorEntity

class MagicalMirror(properties: Properties): MachineBlock(properties, ::MagicalMirrorEntity)
