package com.algorithmlx.ecr.api.geo.client

import com.algorithmlx.ecr.api.geo.GeoRenderType
import com.algorithmlx.ecr.api.mixin.client.RenderTypeAccessor
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import java.util.concurrent.ConcurrentHashMap

internal object BedrockGeoGpuRenderTypes {
    private val cache = ConcurrentHashMap<Key, RenderType>()

    fun get(
        texture: Identifier,
        type: GeoRenderType,
    ): RenderType = cache.computeIfAbsent(Key(texture, type), ::create)

    fun clear() {
        cache.clear()
    }

    private fun create(key: Key): RenderType {
        val setup =
            RenderSetup
                .builder(BedrockGeoGpuPipelines.get(key.type))
                .withTexture("Sampler0", key.texture)
        if (key.type != GeoRenderType.ADDITIVE) {
            setup.useLightmap().useOverlay()
        }
        return RenderTypeAccessor.create(
            "bedrock_geo_${key.type.name.lowercase()}",
            setup.createRenderSetup(),
        )
    }

    private data class Key(
        val texture: Identifier,
        val type: GeoRenderType,
    )
}
