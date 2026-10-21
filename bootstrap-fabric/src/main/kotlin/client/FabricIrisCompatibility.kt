package com.algorithmlx.ecr.fabric.client

import com.algorithmlx.ecr.api.LOGGER
import com.algorithmlx.ecr.api.geo.client.BedrockGeoRenderCompatibility
import net.fabricmc.loader.api.FabricLoader
import net.irisshaders.iris.api.v0.IrisApi

object FabricIrisCompatibility {
    private const val IRIS_MOD_ID = "iris"

    fun init() {
        if (!FabricLoader.getInstance().isModLoaded(IRIS_MOD_ID)) return

        runCatching {
            val iris = IrisApi.getInstance()
            BedrockGeoRenderCompatibility.installShaderPackInUseDetector(iris::isShaderPackInUse)
            LOGGER.info("Enabled Iris compatibility for the Bedrock GEO renderer")
        }.onFailure { error ->
            BedrockGeoRenderCompatibility.installShaderPackInUseDetector { true }
            LOGGER.warn(
                "Iris is loaded but its API is unavailable; forcing the Bedrock GEO compatibility renderer",
                error
            )
        }
    }
}
