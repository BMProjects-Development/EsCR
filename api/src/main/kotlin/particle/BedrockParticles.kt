package com.algorithmlx.ecr.api.particle

import com.algorithmlx.ecr.api.LOGGER
import com.algorithmlx.ecr.api.particle.file.BedrockParticleFile
import kotlinx.serialization.json.Json
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener

object BedrockParticles : ResourceManagerReloadListener {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    val files = linkedMapOf<Identifier, BedrockParticleFile>()
    val effects = linkedMapOf<String, ParticleEffect>()

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        val loadedFiles = linkedMapOf<Identifier, BedrockParticleFile>()
        resourceManager.listResources("particles") { it.path.endsWith(".bedrock.json") }
            .forEach { (location, resource) ->
                runCatching {
                    resource.openAsReader().use { json.decodeFromString<BedrockParticleFile>(it.readText()) }
                }.onSuccess { loadedFiles[location] = it }
                    .onFailure { LOGGER.warn("Unable to load Bedrock particle {}", location, it) }
            }

        val loadedEffects = loadedFiles.values
            .map(ParticleEffect::fromFile)
            .associateByTo(linkedMapOf()) { it.identifier }
        loadedEffects.values.forEach { effect ->
            effect.events.values
                .asSequence()
                .flatMap(::referencedParticleNames)
                .distinct()
                .forEach { name -> loadedEffects[name]?.let { effect.referencedEffects[name] = it } }
        }

        synchronized(this) {
            files.clear()
            files.putAll(loadedFiles)
            effects.clear()
            effects.putAll(loadedEffects)
        }
    }

    operator fun get(identifier: String): ParticleEffect? = synchronized(this) { effects[identifier] }

    private fun referencedParticleNames(event: BedrockParticleFile.Event): Sequence<String> = sequence {
        event.particle?.let { yield(it.effect) }
        event.sequence?.forEach { yieldAll(referencedParticleNames(it)) }
        event.randomize?.forEach { yieldAll(referencedParticleNames(it.value)) }
    }
}
