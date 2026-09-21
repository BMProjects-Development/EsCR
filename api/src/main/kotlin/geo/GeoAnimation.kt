package com.algorithmlx.ecr.api.geo

import java.util.LinkedHashMap

enum class GeoLoopMode {
    FROM_FILE,
    ONCE,
    LOOP,
    HOLD
}

enum class GeoBlendMode {
    ADDITIVE,
    OVERRIDE
}

enum class AnimationType {
    PLAY_ONCE,
    PLAY_FREEZE,
    PLAY_LOOPED,
    PLAY_REVERSED
}

data class GeoAnimationPlayback(
    val layer: String,
    val animation: String,
    val startTimeSeconds: Double,
    val speed: Float,
    val weight: Float,
    val loop: GeoLoopMode,
    val blend: GeoBlendMode
)

class GeoAnimationState {
    private val playbacks = LinkedHashMap<String, MutablePlayback>()

    @Synchronized
    fun play(
        animation: String,
        type: AnimationType = AnimationType.PLAY_ONCE,
        nowSeconds: Double = Double.NaN
    ) = play(
        MAIN_LAYER,
        animation,
        nowSeconds = nowSeconds,
        speed = if (type == AnimationType.PLAY_REVERSED) -1F else 1F,
        loop =
            when (type) {
                AnimationType.PLAY_ONCE, AnimationType.PLAY_REVERSED -> GeoLoopMode.ONCE
                AnimationType.PLAY_FREEZE -> GeoLoopMode.HOLD
                AnimationType.PLAY_LOOPED -> GeoLoopMode.LOOP
            }
    )

    @Synchronized
    fun play(
        animation: String,
        nowSeconds: Double
    ) = play(animation, AnimationType.PLAY_ONCE, nowSeconds)

    @Synchronized
    fun play(
        layer: String,
        animation: String,
        nowSeconds: Double = Double.NaN,
        speed: Float = 1F,
        weight: Float = 1F,
        loop: GeoLoopMode = GeoLoopMode.FROM_FILE,
        blend: GeoBlendMode = GeoBlendMode.ADDITIVE,
        restart: Boolean = true
    ) {
        require(layer.isNotBlank()) { "Animation layer must not be blank" }
        require(animation.isNotBlank()) { "Animation identifier must not be blank" }
        require(speed.isFinite() && speed != 0F) { "Animation speed must be finite and non-zero" }
        require(weight.isFinite() && weight in 0F..1F) { "Animation weight must be in 0..1" }

        val current = playbacks[layer]
        if (!restart && current?.animation == animation) return

        playbacks[layer] =
            MutablePlayback(
                animation,
                nowSeconds.takeIf(Double::isFinite),
                speed,
                weight,
                loop,
                blend
            )
    }

    @Synchronized
    fun loop(
        layer: String,
        animation: String,
        speed: Float = 1F,
        weight: Float = 1F,
        blend: GeoBlendMode = GeoBlendMode.ADDITIVE
    ) = play(layer, animation, speed = speed, weight = weight, loop = GeoLoopMode.LOOP, blend = blend, restart = false)

    @Synchronized
    fun stop(layer: String) {
        playbacks.remove(layer)
    }

    @Synchronized
    fun clear() {
        playbacks.clear()
    }

    @Synchronized
    fun isPlaying(
        layer: String,
        animation: String? = null
    ): Boolean = playbacks[layer]?.let { animation == null || it.animation == animation } == true

    @Synchronized
    internal fun removeIfCurrent(playback: GeoAnimationPlayback): Boolean {
        val current = playbacks[playback.layer] ?: return false
        if (
            current.animation != playback.animation ||
            current.startTimeSeconds != playback.startTimeSeconds ||
            current.speed != playback.speed ||
            current.loop != playback.loop
        ) {
            return false
        }
        playbacks.remove(playback.layer)
        return true
    }

    @Synchronized
    fun snapshot(nowSeconds: Double): List<GeoAnimationPlayback> =
        playbacks.map { (layer, playback) ->
            val start = playback.startTimeSeconds ?: nowSeconds.also { playback.startTimeSeconds = it }
            GeoAnimationPlayback(
                layer,
                playback.animation,
                start,
                playback.speed,
                playback.weight,
                playback.loop,
                playback.blend
            )
        }

    private data class MutablePlayback(
        val animation: String,
        var startTimeSeconds: Double?,
        val speed: Float,
        val weight: Float,
        val loop: GeoLoopMode,
        val blend: GeoBlendMode
    )

    companion object {
        const val MAIN_LAYER = "main"
    }
}
