package com.algorithmlx.ecr.api.block

import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3

class StructurePosition private constructor(private val positions: List<BlockPos>) {
    operator fun get(pos: BlockPos) = positions.map(pos::offset)

    class Builder internal constructor() {
        private val positions = mutableListOf<BlockPos>()

        fun pos(pos: BlockPos) {
            positions += pos
        }

        fun pos(x: Int, y: Int, z: Int) = pos(BlockPos(x, y, z))

        fun pos(x: Double, y: Double, z: Double) = pos(x.toInt(), y.toInt(), z.toInt())

        fun pos(vec: Vec3) = pos(vec.x, vec.y, vec.z)

        internal fun build() = StructurePosition(positions.toList())
    }
}

fun structurePosition(init: StructurePosition.Builder.() -> Unit): StructurePosition =
    StructurePosition.Builder().apply(init).build()
