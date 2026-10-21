package com.algorithmlx.ecr.api.item

import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.entity.BlockEntity

interface BoundGemTarget {
    companion object {
        private val adapters = linkedMapOf<Identifier, (BlockEntity) -> Boolean>()

        @JvmStatic
        fun register(id: Identifier, accepts: (BlockEntity) -> Boolean) {
            check(adapters.putIfAbsent(id, accepts) == null) { "Duplicate Bound Gem target adapter: $id" }
        }

        @JvmStatic
        fun isTarget(target: BlockEntity?): Boolean = target != null &&
            (target is BoundGemTarget || adapters.values.any { it(target) })
    }
}
