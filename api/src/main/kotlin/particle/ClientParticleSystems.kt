package com.algorithmlx.ecr.api.particle

import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.Level
import java.util.Collections
import java.util.WeakHashMap

object ClientParticleSystems {
    private val systems = Collections.synchronizedMap(WeakHashMap<ClientLevel, ParticleSystem>())

    fun system(level: ClientLevel): ParticleSystem = systems.computeIfAbsent(level, ParticleSystem::create)

    fun system(level: Level) = system(level as ClientLevel)

    fun get(level: ClientLevel): ParticleSystem? = systems[level]

    fun clear() = systems.clear()
}
