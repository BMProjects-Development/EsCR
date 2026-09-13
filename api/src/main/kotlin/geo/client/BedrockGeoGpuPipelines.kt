package com.algorithmlx.ecr.api.geo.client

import com.algorithmlx.ecr.api.geo.GeoRenderType
import com.algorithmlx.ecr.api.mixin.client.RenderPipelinesAccessor
import com.algorithmlx.ecr.api.utils.ecRL
import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.UniformType
import com.mojang.blaze3d.vertex.VertexFormat

object BedrockGeoGpuPipelines {
    @JvmField
    val VERTEX_FORMAT: VertexFormat =
        VertexFormat
            .builder(0)
            .addAttribute("Position", GpuFormat.RGB32_FLOAT)
            .addAttribute("UV0", GpuFormat.RG32_FLOAT)
            .addAttribute("Normal", GpuFormat.RGB32_FLOAT)
            .addAttribute("BoneIndex", GpuFormat.R32_SINT)
            .build()

    private val geoBindings =
        BindGroupLayout
            .builder()
            .withUniform("GeoInfo", UniformType.UNIFORM_BUFFER)
            .withUniform("GeoMatrices", UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
            .build()

    private val solid =
        register(
            RenderPipeline
                .builder(RenderPipelinesAccessor.entitySnippet())
                .withLocation("pipeline/bedrock_geo_solid".ecRL)
                .withVertexShader("core/bedrock_geo".ecRL)
                .withBindGroupLayout(geoBindings)
                .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1)
                .withVertexBinding(0, VERTEX_FORMAT)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build(),
        )

    private val cutout =
        register(
            RenderPipeline
                .builder(RenderPipelinesAccessor.entitySnippet())
                .withLocation("pipeline/bedrock_geo_cutout".ecRL)
                .withVertexShader("core/bedrock_geo".ecRL)
                .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                .withBindGroupLayout(geoBindings)
                .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1)
                .withVertexBinding(0, VERTEX_FORMAT)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build(),
        )

    private val translucent =
        register(
            RenderPipeline
                .builder(RenderPipelinesAccessor.entitySnippet())
                .withLocation("pipeline/bedrock_geo_translucent".ecRL)
                .withVertexShader("core/bedrock_geo".ecRL)
                .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                .withShaderDefine("PER_FACE_LIGHTING")
                .withBindGroupLayout(geoBindings)
                .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1)
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false)
                .withVertexBinding(0, VERTEX_FORMAT)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build(),
        )

    private val additive =
        register(
            RenderPipeline
                .builder(RenderPipelinesAccessor.entityEmissiveSnippet())
                .withLocation("pipeline/bedrock_geo_additive".ecRL)
                .withVertexShader("core/bedrock_geo".ecRL)
                .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                .withShaderDefine("EMISSIVE")
                .withShaderDefine("NO_OVERLAY")
                .withShaderDefine("NO_CARDINAL_LIGHTING")
                .withBindGroupLayout(geoBindings)
                .withColorTargetState(ColorTargetState(BlendFunction.ADDITIVE))
                .withDepthStencilState(DepthStencilState.DEFAULT)
                .withCull(false)
                .withVertexBinding(0, VERTEX_FORMAT)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .build(),
        )

    @JvmStatic
    fun ensureInitialized() = Unit

    fun get(type: GeoRenderType): RenderPipeline =
        when (type) {
            GeoRenderType.SOLID -> solid
            GeoRenderType.CUTOUT -> cutout
            GeoRenderType.TRANSLUCENT -> translucent
            GeoRenderType.ADDITIVE -> additive
        }

    private fun register(pipeline: RenderPipeline): RenderPipeline = RenderPipelinesAccessor.register(pipeline)
}
