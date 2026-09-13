package com.algorithmlx.ecr.api.particle

import com.algorithmlx.ecr.api.mixin.client.RenderPipelinesAccessor
import com.algorithmlx.ecr.api.mixin.client.RenderTypeAccessor
import com.algorithmlx.ecr.api.particle.file.BedrockParticleFile
import com.algorithmlx.ecr.api.utils.ecRL
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import java.util.concurrent.ConcurrentHashMap

object BedrockParticleRenderTypes {
    private val additivePipeline =
        RenderPipelinesAccessor.register(
            RenderPipeline
                .builder(RenderPipelinesAccessor.particleSnippet())
                .withLocation("pipeline/bedrock_particle_additive".ecRL)
                .withColorTargetState(ColorTargetState(BlendFunction.ADDITIVE))
                .build(),
        )
    private val cache = ConcurrentHashMap<Key, RenderType>()

    fun init() = Unit

    internal fun get(
        texture: Identifier,
        material: BedrockParticleFile.Material,
    ): RenderType = cache.computeIfAbsent(Key(texture, material), ::create)

    private fun create(key: Key): RenderType {
        val pipeline =
            when (key.material) {
                BedrockParticleFile.Material.Add -> additivePipeline
                BedrockParticleFile.Material.Cutout -> RenderPipelines.OPAQUE_PARTICLE
                BedrockParticleFile.Material.Blend -> RenderPipelines.TRANSLUCENT_PARTICLE
            }
        val setup =
            RenderSetup
                .builder(pipeline)
                .withTexture("Sampler0", key.texture)
                .useLightmap()
        if (key.material.needsSorting) setup.sortOnUpload()
        return RenderTypeAccessor.create(
            "bedrock_particle_${key.material.name.lowercase()}",
            setup.createRenderSetup(),
        )
    }

    private data class Key(
        val texture: Identifier,
        val material: BedrockParticleFile.Material,
    )
}
