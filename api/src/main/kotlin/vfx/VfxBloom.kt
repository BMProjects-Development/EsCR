package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.mixin.client.RenderPipelinesAccessor
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.pipeline.BindGroupLayout
import com.mojang.renderpearl.api.pipeline.BlendFactor
import com.mojang.renderpearl.api.pipeline.BlendFunction
import com.mojang.renderpearl.api.pipeline.ColorTargetState
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import com.mojang.renderpearl.api.pipeline.UniformType
import com.mojang.renderpearl.api.textures.FilterMode
import net.minecraft.resources.Identifier
import java.util.Optional

internal object VfxBloom {
    private val blurPipeline = pipeline("vfx_blur", true)
    private val compositePipeline = pipeline("vfx_glow", false)
    private val uniforms = VfxUniformBuffer()
    private var ping: TextureTarget? = null
    private var pong: TextureTarget? = null

    fun render(target: RenderTarget, mask: RenderTarget) {
        ensureTargets(target)
        val horizontal = requireNotNull(ping)
        val vertical = requireNotNull(pong)
        val sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
        try {
            var source: RenderTarget = mask
            for (step in listOf(2f, 4f, 8f)) {
                for ((destination, direction) in listOf(horizontal to (step to 0f), vertical to (0f to step))) {
                    val parameters = uniforms.write(16) {
                        Std140Builder.intoBuffer(it).putVec2(direction.first, direction.second).putVec2(target.width.toFloat(), target.height.toFloat())
                    }
                    RenderSystem.getDevice().createCommandEncoder().createRenderPass({ "VFX glow blur" }, requireNotNull(destination.colorTextureView), Optional.empty()).use { pass ->
                        pass.setPipeline(RenderSystem.getCompiledPipeline(blurPipeline))
                        pass.setUniform("InSampler", requireNotNull(source.colorTextureView), sampler)
                        pass.setUniform("VfxBlur", parameters)
                        pass.draw(3, 1, 0, 0)
                    }
                    source = destination
                }
            }
            RenderSystem.getDevice().createCommandEncoder().createRenderPass({ "VFX glow composite" }, requireNotNull(target.colorTextureView), Optional.empty()).use { pass ->
                pass.setPipeline(RenderSystem.getCompiledPipeline(compositePipeline))
                pass.setUniform("InSampler", requireNotNull(vertical.colorTextureView), sampler)
                pass.draw(3, 1, 0, 0)
            }
        } finally {
            uniforms.endFrame()
        }
    }

    private fun ensureTargets(target: RenderTarget) {
        if (ping?.width == target.width && ping?.height == target.height) return
        ping?.destroyBuffers()
        pong?.destroyBuffers()
        ping = TextureTarget("VFX horizontal glow", target.width, target.height, GpuFormat.RGBA16_FLOAT, null)
        pong = TextureTarget("VFX vertical glow", target.width, target.height, GpuFormat.RGBA16_FLOAT, null)
    }

    private fun pipeline(name: String, blur: Boolean): RenderPipeline {
        val bindings = BindGroupLayout.builder().withUniform("InSampler", UniformType.COMBINED_IMAGE_SAMPLER)
        if (blur) bindings.withUniform("VfxBlur", UniformType.UNIFORM_BUFFER)
        val blending = BlendFunction(BlendFactor.ONE, BlendFactor.ONE, BlendFactor.ZERO, BlendFactor.ONE)
        return RenderPipelinesAccessor.register(RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("escr", "pipeline/vfx/$name"))
            .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("escr", "post/$name"))
            .withBindGroupLayout(bindings.build())
            .withCull(false)
            .withDepthStencilState(Optional.empty())
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withColorTargetState(if (blur) ColorTargetState(Optional.empty(), GpuFormat.RGBA16_FLOAT, ColorTargetState.WRITE_ALL)
                else ColorTargetState(blending))
            .build())
    }

    fun clear() {
        uniforms.close()
        ping?.destroyBuffers()
        pong?.destroyBuffers()
        ping = null
        pong = null
    }
}
