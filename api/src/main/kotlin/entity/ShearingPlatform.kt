package com.algorithmlx.ecr.api.entity

import net.minecraft.server.level.ServerLevel
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Shearable
import net.minecraft.world.item.ItemStack

fun interface ShearingPlatform {
    fun shear(entity: Entity, level: ServerLevel, tool: ItemStack): Boolean

    companion object {
        val vanilla = ShearingPlatform { entity, level, tool ->
            val shearable = entity as? Shearable
            if (shearable == null || !shearable.readyForShearing()) false else {
                shearable.shear(level, SoundSource.BLOCKS, tool)
                true
            }
        }
        var instance: ShearingPlatform = vanilla
    }
}
