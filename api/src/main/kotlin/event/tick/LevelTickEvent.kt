package com.algorithmlx.ecr.api.event.tick

import com.algorithmlx.ecr.api.event.engine.Event
import net.minecraft.world.level.Level

sealed class LevelTickEvent(val level: Level) : Event() {
    class Pre(level: Level) : LevelTickEvent(level)
    class Post(level: Level) : LevelTickEvent(level)
}
