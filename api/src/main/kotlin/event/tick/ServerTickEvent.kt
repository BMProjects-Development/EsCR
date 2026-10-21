package com.algorithmlx.ecr.api.event.tick

import com.algorithmlx.ecr.api.event.engine.Event
import net.minecraft.server.MinecraftServer

sealed class ServerTickEvent(val server: MinecraftServer) : Event() {
    class Pre(server: MinecraftServer) : ServerTickEvent(server)
    class Post(server: MinecraftServer) : ServerTickEvent(server)
}
