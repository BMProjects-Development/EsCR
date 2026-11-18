package com.algorithmlx.ecr.common.machine

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level

interface Machine {
    fun clientTick(level: Level) = Unit
    fun serverTick(level: ServerLevel) = Unit
}
