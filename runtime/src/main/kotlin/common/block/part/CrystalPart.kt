package com.algorithmlx.ecr.common.block.part

import com.algorithmlx.ecr.api.block.PartRepresentable

enum class CrystalPart(override val id: String): PartRepresentable {
    UP("crystal_up"),
    DOWN("crystal_down")
}
