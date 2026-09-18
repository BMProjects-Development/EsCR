package com.algorithmlx.ecr.api.geo.client

import com.algorithmlx.ecr.api.LOGGER
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import com.mojang.renderpearl.api.commands.RenderPass
import com.mojang.renderpearl.api.pipeline.IndexType
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.client.renderer.feature.FeatureFrameContext
import net.minecraft.client.renderer.feature.FeatureRenderer
import net.minecraft.client.renderer.feature.FeatureRendererType
import net.minecraft.client.renderer.oit.OitStage
import net.minecraft.client.renderer.rendertype.PreparedRenderType
import net.minecraft.util.Mth
import org.joml.Matrix3f
import org.joml.Matrix4f
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BedrockGeoGpuFeatureRenderer: FeatureRenderer<BedrockGeoGpuSubmit> {
    private val groups = arrayListOf<List<PreparedBatch>>()
    private val palettes = PaletteStorage()
    private val info = InfoStorage()

    init {
        BedrockGeoGpuMeshCache.acquireRenderer()
    }

    override fun beginPrepare(context: FeatureFrameContext) {
        groups.clear()
        BedrockGeoGpuMeshCache.flushInvalidation()
        palettes.beginFrame()
    }

    override fun prepareGroup(
        context: FeatureFrameContext,
        submits: List<BedrockGeoGpuSubmit>,
        strictlyOrdered: Boolean
    ) {
        if (BedrockGeoGpuRuntime.failed) {
            groups.add(emptyList())
            return
        }

        val batches = try {
            submits.groupBy(BedrockGeoGpuSubmit::batchKey).values.mapNotNull(::prepareBatch)
        } catch (error: Throwable) {
            BedrockGeoGpuRuntime.disable()
            LOGGER.error("Disabling Bedrock GEO GPU rendering after a prepare failure", error)
            emptyList()
        }
        groups.add(batches)
    }

    override fun executeGroup(
        context: FeatureFrameContext,
        stage: OitStage?,
        renderPass: RenderPass,
        groupIndex: Int,
        submits: List<BedrockGeoGpuSubmit>,
        strictlyOrdered: Boolean
    ) {
        if (BedrockGeoGpuRuntime.failed) return
        try {
            groups[groupIndex].forEach { draw(renderPass, stage, it) }
        } catch (error: Throwable) {
            BedrockGeoGpuRuntime.disable()
            LOGGER.error("Disabling Bedrock GEO GPU rendering after a draw failure", error)
        }
    }

    override fun finishExecute(context: FeatureFrameContext) {
        groups.clear()
        palettes.endFrame()
        info.endFrame()
    }

    override fun close() {
        palettes.close()
        info.close()
        BedrockGeoGpuMeshCache.releaseRenderer()
    }

    private fun prepareBatch(submits: List<BedrockGeoGpuSubmit>): PreparedBatch? {
        val first = submits.firstOrNull() ?: return null
        val mesh = BedrockGeoGpuMeshCache.getOrCreate(first.data.model) ?: return null
        val paletteSlice = palettes.write(first.data.model, submits)
        val stride = BedrockGeoGpuPacking.paletteStrideTexels(first.data.model)
        return PreparedBatch(
            mesh,
            first.renderType.prepare(),
            paletteSlice,
            info.write(GeoInfo(first.data.model.bones.size, stride)),
            submits.size
        )
    }

    private fun draw(pass: RenderPass, stage: OitStage?, batch: PreparedBatch) {
        val prepared = batch.renderType
        val sequentialIndices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS)
        val indexBuffer = sequentialIndices.getBuffer(batch.mesh.indexCount)
        bind(pass, stage, prepared, batch, indexBuffer, sequentialIndices.type())
    }

    private fun bind(
        pass: RenderPass,
        stage: OitStage?,
        prepared: PreparedRenderType,
        batch: PreparedBatch,
        indexBuffer: GpuBuffer,
        indexType: IndexType
    ) {
        val pipeline = if (stage == null) {
            prepared.pipeline()
        } else {
            requireNotNull(prepared.oitPipelineSet()) {
                "OIT pipeline set is missing for ${prepared.name()}"
            }.getPipeline(stage)
        }
        pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline))
        if (prepared.scissorState().enabled()) {
            val scissor = prepared.scissorState()
            pass.enableScissor(scissor.x(), scissor.y(), scissor.width(), scissor.height())
        } else {
            pass.disableScissor()
        }
        RenderSystem.bindDefaultUniforms(pass)
        pass.setUniform("DynamicTransforms", prepared.dynamicTransforms())
        pass.setUniform("GeoInfo", batch.info)
        pass.setUniform("GeoMatrices", batch.palette)
        pass.setVertexBuffer(0, batch.mesh.vertexBuffer.slice())
        prepared.textures().forEach { texture ->
            pass.setUniform(texture.name(), texture.textureView(), texture.sampler())
        }
        pass.setIndexBuffer(indexBuffer, indexType)
        pass.drawIndexed(batch.mesh.indexCount, batch.instanceCount, 0, 0, 0)
    }

    private data class PreparedBatch(
        val mesh: BedrockGeoGpuMesh,
        val renderType: PreparedRenderType,
        val palette: GpuBufferSlice,
        val info: GpuBufferSlice,
        val instanceCount: Int
    )

    private data class GeoInfo(val boneCount: Int, val paletteStride: Int) {
        fun write(byteBuffer: ByteBuffer) {
            byteBuffer.order(ByteOrder.nativeOrder())
            byteBuffer.putInt(boneCount)
            byteBuffer.putInt(paletteStride)
            byteBuffer.putInt(0)
            byteBuffer.putInt(0)
        }
    }

    private class InfoStorage : AutoCloseable {
        private val retired = arrayListOf<MappableRingBuffer>()
        private var buffer: MappableRingBuffer? = null
        private var writeOffset = 0

        fun write(info: GeoInfo): GpuBufferSlice {
            val alignment = RenderSystem.getDevice().deviceInfo.limits.minUniformOffsetAlignment
            var alignedOffset = Mth.roundToward(writeOffset, alignment)
            var current = buffer
            if (current == null || alignedOffset + GEO_INFO_BYTES > current.size()) {
                current?.let(retired::add)
                val capacity = Mth.smallestEncompassingPowerOfTwo(maxOf(INITIAL_INFO_BYTES, GEO_INFO_BYTES))
                current = MappableRingBuffer(
                    { "Bedrock GEO Info" },
                    GpuBuffer.USAGE_MAP_WRITE or GpuBuffer.USAGE_UNIFORM,
                    capacity
                )
                buffer = current
                alignedOffset = 0
            }

            val slice = current.currentBuffer().slice(alignedOffset.toLong(), GEO_INFO_BYTES.toLong())
            slice.map(false, true).use { view ->
                info.write(view.data().order(ByteOrder.nativeOrder()))
            }
            writeOffset = alignedOffset + GEO_INFO_BYTES
            return slice
        }

        fun endFrame() {
            buffer?.rotate()
            writeOffset = 0
            retired.forEach(MappableRingBuffer::close)
            retired.clear()
        }

        override fun close() {
            buffer?.close()
            buffer = null
            retired.forEach(MappableRingBuffer::close)
            retired.clear()
        }
    }

    private class PaletteStorage : AutoCloseable {
        private val retired = arrayListOf<MappableRingBuffer>()
        private var buffer: MappableRingBuffer? = null
        private var writeOffset = 0

        fun beginFrame() {
            writeOffset = 0
        }

        fun write(model: BakedGeoModel, submits: List<BedrockGeoGpuSubmit>): GpuBufferSlice {
            val size = BedrockGeoGpuPacking.paletteBytes(model, submits.size)
            val alignment = RenderSystem.getDevice().deviceInfo.limits.minUniformOffsetAlignment
            var alignedOffset = Mth.roundToward(writeOffset, alignment)
            var current = buffer
            if (current == null || alignedOffset + size > current.size()) {
                current?.let(retired::add)
                val capacity = Mth.smallestEncompassingPowerOfTwo(maxOf(INITIAL_PALETTE_BYTES, size))
                current = MappableRingBuffer(
                    { "Bedrock GEO Matrices" },
                    GpuBuffer.USAGE_MAP_WRITE or GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER,
                    capacity
                )
                buffer = current
                alignedOffset = 0
            }

            val slice = current.currentBuffer().slice(alignedOffset.toLong(), size.toLong())
            slice.map(false, true).use { view ->
                writePalette(model, submits, view.data().order(ByteOrder.nativeOrder()))
            }
            writeOffset = alignedOffset + size
            return slice
        }

        fun endFrame() {
            buffer?.rotate()
            retired.forEach(MappableRingBuffer::close)
            retired.clear()
        }

        override fun close() {
            buffer?.close()
            buffer = null
            retired.forEach(MappableRingBuffer::close)
            retired.clear()
        }

        private fun writePalette(
            model: BakedGeoModel,
            submits: List<BedrockGeoGpuSubmit>,
            target: ByteBuffer
        ) {
            val transform = Matrix4f()
            val normal = Matrix3f()
            submits.forEach { submit ->
                target.putFloat(lowShort(submit.packedLight).toFloat())
                target.putFloat(highShort(submit.packedLight).toFloat())
                target.putFloat(lowShort(submit.packedOverlay).toFloat())
                target.putFloat(highShort(submit.packedOverlay).toFloat())

                model.bones.indices.forEach { boneIndex ->
                    transform.set(submit.modelMatrix).mul(submit.data.pose.transforms[boneIndex])
                    normal.set(submit.normalMatrix).mul(submit.data.pose.normalTransforms[boneIndex])
                    putMatrix4(target, transform)
                    putMatrix3(target, normal)
                }
            }
        }

        private fun putMatrix4(target: ByteBuffer, matrix: Matrix4f) {
            target.putFloat(matrix.m00()).putFloat(matrix.m01()).putFloat(matrix.m02()).putFloat(matrix.m03())
            target.putFloat(matrix.m10()).putFloat(matrix.m11()).putFloat(matrix.m12()).putFloat(matrix.m13())
            target.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(matrix.m22()).putFloat(matrix.m23())
            target.putFloat(matrix.m30()).putFloat(matrix.m31()).putFloat(matrix.m32()).putFloat(matrix.m33())
        }

        private fun putMatrix3(target: ByteBuffer, matrix: Matrix3f) {
            target.putFloat(matrix.m00()).putFloat(matrix.m01()).putFloat(matrix.m02()).putFloat(0F)
            target.putFloat(matrix.m10()).putFloat(matrix.m11()).putFloat(matrix.m12()).putFloat(0F)
            target.putFloat(matrix.m20()).putFloat(matrix.m21()).putFloat(matrix.m22()).putFloat(0F)
        }

        private fun lowShort(value: Int): Int = value and 0xFFFF

        private fun highShort(value: Int): Int = value ushr 16 and 0xFFFF
    }

    companion object {
        @JvmField
        val TYPE: FeatureRendererType<BedrockGeoGpuSubmit> = FeatureRendererType.create("Bedrock GEO")

        private const val INITIAL_PALETTE_BYTES = 64 * 1024
        private const val INITIAL_INFO_BYTES = 4 * 1024
        private const val GEO_INFO_BYTES = 16
    }
}
