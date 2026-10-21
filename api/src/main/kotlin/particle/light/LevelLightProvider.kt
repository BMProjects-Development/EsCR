package com.algorithmlx.ecr.api.particle.light

import net.minecraft.core.BlockPos
import net.minecraft.util.LightCoordsUtil
import net.minecraft.world.level.Level
import org.joml.Vector3f

class LevelLightProvider(private val level: Level) : LightProvider {
    override fun query(pos: Vector3f): Int {
        val block = BlockPos.containing(pos.x.toDouble(), pos.y.toDouble(), pos.z.toDouble())
        if (!level.isLoaded(block)) return LightCoordsUtil.FULL_BRIGHT

        return LightCoordsUtil.getLightCoords(level, block)
    }
}
