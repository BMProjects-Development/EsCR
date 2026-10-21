package com.algorithmlx.ecr.api.vfx

import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.pipeline.RenderTarget
import com.mojang.blaze3d.pipeline.TextureTarget
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.GpuFormat
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.textures.FilterMode
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import kotlinx.serialization.json.JsonObject
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.StagedVertexBuffer
import net.minecraft.client.renderer.rendertype.PreparedRenderType
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import org.joml.Vector4f
import java.util.Optional
import java.util.OptionalDouble

internal object VfxGpuGeometry {
    private class Submission(
        val type: RenderType, val glowType: RenderType, val state: VfxMaterialSnapshot,
        val pose: PoseStack.Pose, val vertices: (PoseStack.Pose, VertexConsumer) -> Unit
    )
    private class Batch(
        val draw: StagedVertexBuffer.Draw, val type: PreparedRenderType, val glowType: PreparedRenderType,
        val material: GpuBufferSlice, val glowMaterial: GpuBufferSlice, val uniforms: GpuBufferSlice?, val glow: Float
    )
    private val submissions = arrayListOf<Submission>()
    private var vertices: StagedVertexBuffer? = null
    private val uniforms = VfxUniformBuffer()
    private var depth: TextureTarget? = null
    private var glow: TextureTarget? = null

    fun beginFrame() = submissions.clear()

    fun submit(material: JsonObject, texture: Identifier?, state: VfxMaterialSnapshot, pose: PoseStack.Pose, draw: (PoseStack.Pose, VertexConsumer) -> Unit) =
        submit(material, texture, state, pose, PrimitiveTopology.QUADS, draw)

    fun submitTriangles(material: JsonObject, texture: Identifier, state: VfxMaterialSnapshot, pose: PoseStack.Pose, draw: (PoseStack.Pose, VertexConsumer) -> Unit) =
        submit(material, texture, state, pose, PrimitiveTopology.TRIANGLES, draw)

    private fun submit(material: JsonObject, texture: Identifier?, state: VfxMaterialSnapshot, pose: PoseStack.Pose, topology: PrimitiveTopology, draw: (PoseStack.Pose, VertexConsumer) -> Unit) {
        val type = if (texture == null) VfxMaterialRenderTypes.particle(material) else VfxMaterialRenderTypes.model(material, texture, topology = topology)
        val glowType = if (texture == null) VfxMaterialRenderTypes.particle(material, true) else VfxMaterialRenderTypes.model(material, texture, true, topology)
        submissions += Submission(type, glowType, state, pose.copy(), draw)
    }

    fun render(target: RenderTarget) {
        if (submissions.isEmpty()) return
        val depth = ensureDepth(target)
        depth.copyDepthFrom(target)
        val staged = vertices ?: StagedVertexBuffer({ "VFX material geometry" }, 1 shl 20).also { vertices = it }
        val modelView = RenderSystem.getModelViewStack()
        val camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState
        modelView.pushMatrix().mul(camera.viewRotationMatrix)
        try {
            val batches = submissions.groupBy { it.type to it.state }.values.map { group ->
                val first = group.first()
                val draw = staged.appendDraw(first.type.format(), first.type.primitiveTopology())
                val consumer = staged.getVertexBuilder(draw)
                group.forEach { it.vertices(it.pose, consumer) }
                val state = first.state
                fun parameters(glowPass: Float) = uniforms.write(32) {
                    val zeroToOne = RenderSystem.getDevice().deviceInfo.isZZeroToOne
                    Std140Builder.intoBuffer(it).putFloat(state.softness).putFloat(state.glow).putFloat(glowPass).putFloat(state.time)
                        .putFloat(if (zeroToOne) 1f else 2f).putFloat(if (zeroToOne) 0f else -1f).putVec2(0f, 0f)
                }
                val authored = if (state.uniforms.isEmpty()) null else uniforms.write(state.uniformBytes) { state.writeUniforms(Std140Builder.intoBuffer(it)) }
                Batch(draw, first.type.prepare(), first.glowType.prepare(), parameters(0f), parameters(1f), authored, state.glow)
            }
            staged.upload()
            batches.forEach { staged.requestIndexCount(it.draw) }
            RenderSystem.getDevice().createCommandEncoder().createRenderPass({ "VFX materials" }, requireNotNull(target.colorTextureView),
                Optional.empty(), target.depthTextureView, OptionalDouble.empty()).use { pass ->
                batches.forEach { draw(pass, staged, it, depth, false) }
            }
            val glowing = batches.filter { it.glow > 0f }
            if (glowing.isNotEmpty()) {
                val mask = ensureGlow(target)
                val encoder = RenderSystem.getDevice().createCommandEncoder()
                encoder.clearColorTexture(requireNotNull(mask.colorTexture), Vector4f())
                encoder.createRenderPass({ "VFX glow mask" }, requireNotNull(mask.colorTextureView), Optional.empty(), target.depthTextureView,
                    OptionalDouble.empty()).use { pass -> glowing.forEach { draw(pass, staged, it, depth, true) } }
                VfxBloom.render(target, mask)
            }
        } finally {
            modelView.popMatrix()
            staged.endFrame()
            uniforms.endFrame()
            submissions.clear()
        }
    }

    private fun draw(pass: RenderPass, staged: StagedVertexBuffer, batch: Batch, depth: RenderTarget, glowing: Boolean) {
        val info = staged.getExecuteInfo(batch.draw) ?: return
        val type = if (glowing) batch.glowType else batch.type
        pass.setPipeline(RenderSystem.getCompiledPipeline(type.pipeline()))
        pass.disableScissor()
        RenderSystem.bindDefaultUniforms(pass)
        pass.setUniform("DynamicTransforms", type.dynamicTransforms())
        pass.setUniform("VfxMaterial", if (glowing) batch.glowMaterial else batch.material)
        batch.uniforms?.let { pass.setUniform("VfxUniforms", it) }
        pass.setUniform("SceneDepth", requireNotNull(depth.depthTextureView), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST))
        type.textures().forEach { pass.setUniform(it.name(), it.textureView(), it.sampler()) }
        pass.setVertexBuffer(0, info.vertexBuffer().slice())
        pass.setIndexBuffer(info.indexBuffer(), info.indexType())
        pass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0)
    }

    private fun ensureDepth(target: RenderTarget): TextureTarget {
        val current = depth
        if (current != null && current.width == target.width && current.height == target.height) return current
        current?.destroyBuffers()
        return TextureTarget("VFX scene depth", target.width, target.height, GpuFormat.RGBA8_UNORM,
            requireNotNull(target.depthTexture).format).also { depth = it }
    }

    private fun ensureGlow(target: RenderTarget): TextureTarget {
        val current = glow
        if (current != null && current.width == target.width && current.height == target.height) return current
        current?.destroyBuffers()
        return TextureTarget("VFX glow", target.width, target.height, GpuFormat.RGBA16_FLOAT, null).also { glow = it }
    }

    fun clear() {
        submissions.clear()
        vertices?.close()
        vertices = null
        uniforms.close()
        depth?.destroyBuffers()
        depth = null
        glow?.destroyBuffers()
        glow = null
        VfxBloom.clear()
    }
}
