package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.resolveMRUDevice
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity

object MIMLinks {
    fun isBound(stack: ItemStack): Boolean = (stack.item as? BoundGem)?.getBoundPos(stack) != null

    fun resolve(level: Level, stack: ItemStack): BlockEntity? {
        val pos = resolvePosition(level, stack) ?: return null
        return level.getBlockEntity(pos)?.takeUnless { it.isRemoved }
    }

    fun resolveMRU(level: Level, stack: ItemStack): MRUDevice? =
        resolvePosition(level, stack)?.let(level::resolveMRUDevice)

    fun resolvePosition(level: Level, stack: ItemStack): BlockPos? {
        val gem = stack.item as? BoundGem ?: return null
        val dimension = gem.getWorld(stack)
        if (dimension != null && dimension != level.dimension()) return null
        val pos = gem.getBoundPos(stack) ?: return null
        return pos.takeIf(level::isLoaded)
    }
}
