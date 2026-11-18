package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.common.block.entity.MagmaticFurnaceEntity
import com.algorithmlx.ecr.common.machine.MachineBlock

class MagmaticFurnace(properties: Properties, val tier: Tier = Tier.BASIC): MachineBlock(properties, ::MagmaticFurnaceEntity) {
    enum class Tier(val speed: Int) { BASIC(1), MAGICAL(2), PALE(3), VOID(4) }
}
