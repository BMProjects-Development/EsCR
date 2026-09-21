package com.algorithmlx.ecr.api.particle

import com.algorithmlx.ecr.api.particle.file.BedrockParticleFile
import com.algorithmlx.ecr.api.particle.file.ParticleComponents
import net.minecraft.resources.Identifier
import net.minecraft.sounds.SoundEvent

class ParticleEffect(
    val file: String,
    val identifier: String,
    val material: BedrockParticleFile.Material,
    val components: ParticleComponents,
    val curves: Map<String, BedrockParticleFile.Curve>,
    val events: Map<String, BedrockParticleFile.Event>,
    val texture: Identifier?,
    referencedEffects: Map<String, ParticleEffect> = emptyMap(),
    val referencedSounds: Map<String, SoundEvent> = emptyMap()
) {
    val referencedEffects: MutableMap<String, ParticleEffect> = referencedEffects.toMutableMap()
    val renderPass = texture?.let { RenderPass(material, it) }

    data class RenderPass(
        val material: BedrockParticleFile.Material,
        val texture: Identifier
    ) {
        val renderType by lazy { BedrockParticleRenderTypes.get(texture, material) }
    }

    companion object {
        fun fromFile(file: BedrockParticleFile): ParticleEffect {
            val description = file.particleEffect.description
            val texture = description.basicRenderParameters.texture
                .takeUnless { it.isBlank() }
                ?.let(Identifier::parse)
            return ParticleEffect(
                file = description.identifier,
                identifier = description.identifier,
                material = description.basicRenderParameters.material,
                components = file.particleEffect.components,
                curves = file.particleEffect.curves,
                events = file.particleEffect.events,
                texture = texture
            )
        }
    }
}
