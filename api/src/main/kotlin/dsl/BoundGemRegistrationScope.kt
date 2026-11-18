package com.algorithmlx.ecr.api.dsl

import com.algorithmlx.ecr.api.ECRAPIScope
import com.algorithmlx.ecr.api.item.BoundGemTarget
import com.algorithmlx.ecr.api.item.BoundGemTooltips
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity

class BoundGemRegistrationScope(namespace: String): ECRAPIScope(namespace) {
    fun target(path: String, accepts: (BlockEntity) -> Boolean) = BoundGemTarget.register(id(path), accepts)
    fun tooltip(path: String, provider: (Level, BlockPos) -> List<Component>?) = BoundGemTooltips.register(id(path), provider)
}
