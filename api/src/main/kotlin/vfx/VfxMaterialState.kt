package com.algorithmlx.ecr.api.vfx

import com.mojang.blaze3d.buffers.Std140Builder
import com.mojang.blaze3d.buffers.Std140SizeCalculator
import kotlinx.serialization.json.JsonObject

internal data class VfxUniformSample(val name: String, val components: List<Float>)

internal data class VfxMaterialSnapshot(
    val softness: Float,
    val glow: Float,
    val time: Float,
    val uniforms: List<VfxUniformSample>
) {
    val uniformBytes: Int get() = Std140SizeCalculator().also { size ->
        uniforms.forEach { field ->
            when (field.components.size) {
                1 -> size.putFloat()
                2 -> size.putVec2()
                3 -> size.putVec3()
                4 -> size.putVec4()
            }
        }
    }.get().let { ((it + 15) / 16 * 16).coerceAtLeast(16) }

    fun writeUniforms(target: Std140Builder) {
        uniforms.forEach { field ->
            val v = field.components
            when (v.size) {
                1 -> target.putFloat(v[0])
                2 -> target.putVec2(v[0], v[1])
                3 -> target.putVec3(v[0], v[1], v[2])
                4 -> target.putVec4(v[0], v[1], v[2], v[3])
            }
        }
    }
}

internal class VfxMaterialState(private val material: JsonObject) {
    private val softness = material["softness"].value()
    private val glow = material["glow"].value()
    private val fields = VfxUniforms.parse(material)

    val required: Boolean get() = required(material)

    fun sample(node: VfxNodeRuntime): VfxMaterialSnapshot? = if (!required) null else VfxMaterialSnapshot(
        node.sample("material.softness", softness).coerceAtLeast(0f),
        node.sample("material.glow", glow).coerceAtLeast(0f),
        node.instance.time,
        fields.map { VfxUniformSample(it.name, it.sample(node).toList()) }
    )

    companion object {
        fun required(material: JsonObject): Boolean = "softness" in material || "glow" in material ||
            material.array("uniforms").isNotEmpty() || material.array("samplers").isNotEmpty()
    }
}
