package com.algorithmlx.ecr.api.geo.client

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.buffers.GpuBuffer
import org.lwjgl.system.MemoryUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.IdentityHashMap

internal class BedrockGeoGpuMesh(
    val vertexBuffer: GpuBuffer,
    val vertexCount: Int
) : AutoCloseable {
    val indexCount: Int = vertexCount / 4 * 6

    override fun close() {
        vertexBuffer.close()
    }
}

internal object BedrockGeoGpuMeshCache {
    private val meshes = IdentityHashMap<BakedGeoModel, BedrockGeoGpuMesh>()
    private var rendererUsers = 0

    @Volatile
    private var invalidationRequested = false

    fun getOrCreate(model: BakedGeoModel): BedrockGeoGpuMesh? {
        flushInvalidation()
        meshes[model]?.let { return it }

        val vertexCount = BedrockGeoGpuPacking.vertexCount(model)
        if (vertexCount == 0) return null
        val vertexData = MemoryUtil.memAlloc(vertexCount * BedrockGeoGpuPipelines.VERTEX_FORMAT.vertexSize)
            .order(ByteOrder.nativeOrder())
        return try {
            BedrockGeoGpuPacking.writeVertices(model, vertexData)
            vertexData.flip()
            val buffer = RenderSystem.getDevice().createBuffer(
                { "Bedrock GEO ${model.identifier}" },
                GpuBuffer.USAGE_VERTEX,
                vertexData
            )
            BedrockGeoGpuMesh(buffer, vertexCount).also { meshes[model] = it }
        } finally {
            MemoryUtil.memFree(vertexData)
        }
    }

    fun requestInvalidation() {
        invalidationRequested = true
    }

    fun acquireRenderer() {
        rendererUsers++
    }

    fun releaseRenderer() {
        check(rendererUsers > 0) { "Bedrock GEO GPU renderer released without being acquired" }
        rendererUsers--
        if (rendererUsers == 0) close()
    }

    fun close() {
        meshes.values.forEach(BedrockGeoGpuMesh::close)
        meshes.clear()
        invalidationRequested = false
    }

    internal fun flushInvalidation() {
        if (!invalidationRequested) return
        close()
    }
}

internal object BedrockGeoGpuPacking {
    const val MATRIX_TEXELS_PER_BONE = 7
    const val METADATA_TEXELS_PER_INSTANCE = 1
    const val FLOATS_PER_TEXEL = 4

    fun vertexCount(model: BakedGeoModel): Int =
        model.bones.sumOf { bone -> bone.quads.size * 4 }

    fun paletteStrideTexels(model: BakedGeoModel): Int =
        METADATA_TEXELS_PER_INSTANCE + model.bones.size * MATRIX_TEXELS_PER_BONE

    fun paletteBytes(model: BakedGeoModel, instanceCount: Int): Int =
        paletteStrideTexels(model) * FLOATS_PER_TEXEL * Float.SIZE_BYTES * instanceCount

    fun writeVertices(model: BakedGeoModel, target: ByteBuffer) {
        model.bones.forEachIndexed { boneIndex, bone ->
            bone.quads.forEach { quad ->
                repeat(4) { vertexIndex ->
                    val positionIndex = vertexIndex * 3
                    val uvIndex = vertexIndex * 2
                    target.putFloat(quad.positions[positionIndex])
                    target.putFloat(quad.positions[positionIndex + 1])
                    target.putFloat(quad.positions[positionIndex + 2])
                    target.putFloat(quad.uvs[uvIndex])
                    target.putFloat(quad.uvs[uvIndex + 1])
                    target.putFloat(quad.normalX)
                    target.putFloat(quad.normalY)
                    target.putFloat(quad.normalZ)
                    target.putInt(boneIndex)
                }
            }
        }
    }
}
