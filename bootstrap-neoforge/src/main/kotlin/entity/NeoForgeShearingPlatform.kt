package com.algorithmlx.ecr.neoforge.entity

import com.algorithmlx.ecr.api.entity.ShearingPlatform
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.ItemStack
import net.neoforged.neoforge.common.IShearable

object NeoForgeShearingPlatform: ShearingPlatform {
    override fun shear(entity: Entity, level: ServerLevel, tool: ItemStack): Boolean {
        val shearable = entity as? IShearable ?: return ShearingPlatform.vanilla.shear(entity, level, tool)
        val pos = entity.blockPosition()
        if (!shearable.isShearable(null, tool, level, pos)) return false
        shearable.onSheared(null, tool, level, pos).filterNot(ItemStack::isEmpty).forEach { shearable.spawnShearedDrop(level, pos, it) }
        return true
    }
}
