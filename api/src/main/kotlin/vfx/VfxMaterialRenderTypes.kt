package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.mixin.client.RenderPipelinesAccessor
import com.algorithmlx.ecr.api.mixin.client.RenderTypeAccessor
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.BlendFactor
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.CompareOp
import com.mojang.renderpearl.api.pipeline.DepthStencilState
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.UniformType
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.GpuFormat
import kotlinx.serialization.json.JsonObject
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.Optional

internal object VfxMaterialRenderTypes {
    private data class Key(val material: JsonObject, val texture: Identifier, val model: Boolean, val glowPass: Boolean, val topology: PrimitiveTopology = PrimitiveTopology.QUADS)
    private val types = ConcurrentHashMap<Key, RenderType>()
    private val pipelines = ConcurrentHashMap<Key, RenderPipeline>()
    private val nextPipeline = AtomicInteger()

    fun particle(material: JsonObject, glowPass: Boolean = false): RenderType = types.computeIfAbsent(
        Key(material, Identifier.parse(material.string("texture", "minecraft:textures/particle/flash.png")), false, glowPass), ::create
    )

    fun model(material: JsonObject, texture: Identifier, glowPass: Boolean = false, topology: PrimitiveTopology = PrimitiveTopology.QUADS): RenderType =
        types.computeIfAbsent(Key(material, texture, true, glowPass, topology), ::create)

    fun clear() = types.clear()

    private fun create(key: Key): RenderType {
        val blend = key.material.string("blend", "BLEND")
        val advanced = VfxMaterialState.required(key.material)
        val samplers = VfxSamplers.parse(key.material, setOf("Sampler0", "Sampler1", "Sampler2", "SceneDepth"))
        val pipeline = pipelines.computeIfAbsent(key) {
            val builder = RenderPipeline.builder(if (key.model) RenderPipelinesAccessor.entitySnippet() else RenderPipelinesAccessor.particleSnippet())
                .withLocation(Identifier.fromNamespaceAndPath("escr", "pipeline/vfx/material_${nextPipeline.getAndIncrement()}"))
                .withCull(key.material.bool("cull"))
                .withPrimitiveTopology(key.topology)
                .withDepthStencilState(DepthStencilState(
                    if (key.material.bool("depthTest", true)) CompareOp.GREATER_THAN_OR_EQUAL else CompareOp.ALWAYS_PASS,
                    key.material.bool("depthWrite") && !key.glowPass
                ))
            if (key.model) {
                builder.withShaderDefine("VFX_MODEL")
                builder.withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                    .withShaderDefine("NO_CARDINAL_LIGHTING")
                    .withShaderDefine("NO_OVERLAY")
                if (key.material.string("lighting") != "WORLD") builder.withShaderDefine("EMISSIVE")
            }
            if (advanced) {
                val bindings = BindGroupLayout.builder().withUniform("VfxMaterial", UniformType.UNIFORM_BUFFER)
                    .withUniform("SceneDepth", UniformType.COMBINED_IMAGE_SAMPLER)
                if (key.material.array("uniforms").isNotEmpty()) bindings.withUniform("VfxUniforms", UniformType.UNIFORM_BUFFER)
                samplers.forEach { bindings.withUniform(it.name, UniformType.COMBINED_IMAGE_SAMPLER) }
                builder.withBindGroupLayout(bindings.build())
                builder.withFragmentShader(Identifier.fromNamespaceAndPath("escr", "core/vfx/surface_${if (key.model) "model" else "particle"}"))
                if (blend == "MULTIPLY") builder.withShaderDefine("VFX_MULTIPLY")
                if (key.glowPass) builder.withShaderDefine("VFX_GLOW_PASS")
            }
            key.material.string("shader").takeIf(String::isNotBlank)?.let(Identifier::tryParse)?.let { shader ->
                builder.withFragmentShader(if (shader.path.startsWith("core/")) shader else Identifier.fromNamespaceAndPath(shader.namespace, "core/${shader.path}"))
            }
            if (!advanced && blend == "MULTIPLY" && key.material.string("shader").isBlank())
                builder.withFragmentShader(Identifier.fromNamespaceAndPath("escr", "core/vfx/multiply_${if (key.model) "model" else "particle"}"))
            val blending = when (blend) {
                "ADDITIVE" -> BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE)
                "MULTIPLY" -> BlendFunction(BlendFactor.DST_COLOR, BlendFactor.ZERO, BlendFactor.ZERO, BlendFactor.ONE)
                "OPAQUE", "SOLID", "CUTOUT" -> null
                else -> BlendFunction.TRANSLUCENT
            }
            builder.withColorTargetState(if (key.glowPass)
                ColorTargetState(Optional.of(BlendFunction(BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE)), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL)
            else
                blending?.let(::ColorTargetState) ?: ColorTargetState.DEFAULT)
            if (blend in setOf("OPAQUE", "CUTOUT")) builder.withShaderDefine("ALPHA_CUTOUT", key.material.number("alphaCutoff", 0.1f))
            RenderPipelinesAccessor.register(builder.build())
        }
        val setup = RenderSetup.builder(pipeline).withTexture("Sampler0", key.texture).useLightmap()
        samplers.forEach { setup.withTexture(it.name, it.texture) }
        if (key.model) setup.useOverlay()
        if (blend == "BLEND" || blend == "TRANSLUCENT") setup.sortOnUpload()
        return RenderTypeAccessor.create("vfx_${blend.lowercase()}", setup.createRenderSetup())
    }
}
