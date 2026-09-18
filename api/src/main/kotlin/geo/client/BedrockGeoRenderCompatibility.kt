package com.algorithmlx.ecr.api.geo.client

import com.algorithmlx.ecr.api.LOGGER
import java.util.concurrent.atomic.AtomicBoolean

object BedrockGeoRenderCompatibility {
    @Volatile
    private var shaderPackInUseDetector: () -> Boolean = { false }
    private val loggedDetectorFailure = AtomicBoolean()

    @JvmStatic
    fun installShaderPackInUseDetector(detector: () -> Boolean) {
        shaderPackInUseDetector = detector
        loggedDetectorFailure.set(false)
    }

    internal fun canUseGpuRendering(): Boolean = try {
        !shaderPackInUseDetector()
    } catch (error: Throwable) {
        if (loggedDetectorFailure.compareAndSet(false, true)) {
            LOGGER.error(
                "Unable to query shader-pack state; using the Bedrock GEO compatibility renderer",
                error
            )
        }
        false
    }
}
