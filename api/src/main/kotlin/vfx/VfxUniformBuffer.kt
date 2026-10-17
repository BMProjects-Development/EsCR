package com.algorithmlx.ecr.api.vfx

import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.renderpearl.api.buffers.GpuBuffer
import com.mojang.renderpearl.api.buffers.GpuBufferSlice
import net.minecraft.client.renderer.MappableRingBuffer
import net.minecraft.util.Mth
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class VfxUniformBuffer : AutoCloseable {
    private var buffer: MappableRingBuffer? = null
    private val retired = arrayListOf<MappableRingBuffer>()
    private var offset = 0

    fun write(size: Int, writer: (ByteBuffer) -> Unit): GpuBufferSlice {
        val alignment = RenderSystem.getDevice().deviceInfo.limits.minUniformOffsetAlignment
        var aligned = Mth.roundToward(offset, alignment)
        var current = buffer
        if (current == null || aligned + size > current.size()) {
            current?.let(retired::add)
            current = MappableRingBuffer({ "VFX material uniforms" }, GpuBuffer.USAGE_MAP_WRITE or GpuBuffer.USAGE_UNIFORM,
                Mth.smallestEncompassingPowerOfTwo(maxOf(16384, size, current?.size()?.times(2) ?: 0)))
            buffer = current
            aligned = 0
        }
        val slice = current.currentBuffer().slice(aligned.toLong(), size.toLong())
        slice.map(false, true).use { writer(it.data().order(ByteOrder.nativeOrder())) }
        offset = aligned + size
        return slice
    }

    fun endFrame() {
        buffer?.rotate()
        offset = 0
        retired.forEach(MappableRingBuffer::close)
        retired.clear()
    }

    override fun close() {
        buffer?.close()
        buffer = null
        offset = 0
        retired.forEach(MappableRingBuffer::close)
        retired.clear()
    }
}
