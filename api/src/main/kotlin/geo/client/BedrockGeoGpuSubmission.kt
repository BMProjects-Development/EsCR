package com.algorithmlx.ecr.api.geo.client

import net.minecraft.client.renderer.feature.FeatureRendererType
import net.minecraft.client.renderer.feature.submit.BatchableSubmit
import net.minecraft.client.renderer.rendertype.RenderType
import org.joml.Matrix3f
import org.joml.Matrix4f

interface BedrockGeoGpuSubmitCollector {
    fun submitBedrockGeoGpu(submit: BedrockGeoGpuSubmit)
}

class BedrockGeoGpuSubmit(
    val data: BedrockGeoRenderData,
    val modelMatrix: Matrix4f,
    val normalMatrix: Matrix3f,
    val renderType: RenderType,
    val packedLight: Int,
    val packedOverlay: Int
) : BatchableSubmit {
    private val key = BedrockGeoGpuBatchKey(data.model, data.texture, data.renderType)

    override fun batchKey(): Any = key

    override fun featureType(): FeatureRendererType<BedrockGeoGpuSubmit> =
        BedrockGeoGpuFeatureRenderer.TYPE
}

private class BedrockGeoGpuBatchKey(
    private val model: BakedGeoModel,
    private val texture: net.minecraft.resources.Identifier,
    private val renderType: com.algorithmlx.ecr.api.geo.GeoRenderType
) {
    override fun equals(other: Any?): Boolean =
        other is BedrockGeoGpuBatchKey &&
            model === other.model &&
            texture == other.texture &&
            renderType == other.renderType

    override fun hashCode(): Int {
        var result = System.identityHashCode(model)
        result = 31 * result + texture.hashCode()
        result = 31 * result + renderType.hashCode()
        return result
    }
}

internal object BedrockGeoGpuRuntime {
    @Volatile
    var failed = false
        private set

    fun disable() {
        failed = true
    }

    fun reset() {
        failed = false
    }
}
