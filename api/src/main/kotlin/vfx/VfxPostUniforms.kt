package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.mixin.client.VfxPostChainAccessor
import com.algorithmlx.ecr.api.mixin.client.VfxPostPassAccessor
import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.systems.RenderSystem
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import net.minecraft.client.renderer.PostChain
import net.minecraft.client.renderer.PostChainConfig
import net.minecraft.client.renderer.UniformValue
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class VfxPostUniforms(raw: JsonObject, private val config: PostChainConfig, private val properties: JsonObject) {
    private val buffers = hashMapOf<Pair<Int, String>, ByteBuffer>()
    private val names = raw.array("passes").map { pass ->
        pass.obj().obj("uniforms").mapValues { (_, fields) ->
            (fields as? JsonArray).orEmpty().map { it.obj().string("name") }
        }
    }
    private val authored = VfxUniforms.parse(properties).associateBy { it.name }
    private val fallback = mapOf(
        "Strength" to properties["strength"].value(1f), "Radius" to properties["radius"].value(0.25f),
        "Width" to properties["width"].value(0.08f), "Opacity" to properties["opacity"].value(1f)
    )

    fun upload(chain: PostChain, node: VfxNodeRuntime, center: FloatArray) {
        val encoder = RenderSystem.getDevice().createCommandEncoder()
        (chain as VfxPostChainAccessor).passes.forEachIndexed { index, pass ->
            (pass as VfxPostPassAccessor).customUniforms.forEach { (block, gpu) ->
                val bytes = buffers.getOrPut(index to block) { ByteBuffer.allocateDirect(gpu.size().toInt()).order(ByteOrder.nativeOrder()) }
                bytes.clear()
                val writer = Std140Builder.intoBuffer(bytes)
                config.passes()[index].uniforms()[block].orEmpty().forEachIndexed { fieldIndex, default ->
                    val name = names[index][block]?.getOrNull(fieldIndex).orEmpty()
                    val sample = authored[name]?.sample(node) ?: when (name) {
                        "EffectTime" -> floatArrayOf(node.instance.time)
                        "CenterX" -> floatArrayOf(properties.number("centerX", center[0]))
                        "CenterY" -> floatArrayOf(properties.number("centerY", center[1]))
                        else -> fallback[name]?.let { floatArrayOf(node.sample("uniforms.$name", it)) }
                    }
                    if (sample == null) default.writeTo(writer) else write(writer, default, sample)
                }
                bytes.position(0)
                encoder.writeToBuffer(gpu.slice(), bytes)
            }
        }
    }

    private fun write(writer: Std140Builder, default: UniformValue, values: FloatArray) {
        fun component(index: Int) = values.getOrElse(index) { 0f }
        when (default.type()) {
            UniformValue.Type.FLOAT -> writer.putFloat(component(0))
            UniformValue.Type.INT -> writer.putInt(component(0).toInt())
            UniformValue.Type.VEC2 -> writer.putVec2(component(0), component(1))
            UniformValue.Type.VEC3 -> writer.putVec3(component(0), component(1), component(2))
            UniformValue.Type.IVEC3 -> writer.putIVec3(component(0).toInt(), component(1).toInt(), component(2).toInt())
            UniformValue.Type.VEC4 -> writer.putVec4(component(0), component(1), component(2), component(3))
            else -> default.writeTo(writer)
        }
    }
}
