package com.algorithmlx.ecr.api.molang.runtime

import kotlin.math.absoluteValue
import kotlin.math.truncate
import kotlin.random.Random

object Math {
    const val PI = kotlin.math.PI.toFloat()

    @JvmStatic
    fun cos(value: Float) = kotlin.math.cos(value * kotlin.math.PI / 180.0).toFloat()
    @JvmStatic
    fun sin(value: Float) = kotlin.math.sin(value * kotlin.math.PI / 180.0).toFloat()
    @JvmStatic
    fun tan(value: Float) = kotlin.math.tan(value * kotlin.math.PI / 180.0).toFloat()
    @JvmStatic
    fun floor(value: Float) = kotlin.math.floor(value)
    @JvmStatic
    fun ceil(value: Float) = kotlin.math.ceil(value)
    @JvmStatic
    fun round(value: Float) = kotlin.math.round(value)
    @JvmStatic
    fun trunc(value: Float) = truncate(value)
    @JvmStatic
    fun abs(value: Float) = value.absoluteValue
    @JvmStatic
    fun clamp(value: Float, min: Float, max: Float) = value.coerceIn(min, max)
    @JvmStatic
    fun random(low: Float, high: Float) = Random.nextFloat() * (high - low) + low
    @JvmStatic
    fun min(left: Float, right: Float) = kotlin.math.min(left, right)
    @JvmStatic
    fun max(left: Float, right: Float) = kotlin.math.max(left, right)
    @JvmStatic
    fun sqrt(value: Float) = kotlin.math.sqrt(value)
    @JvmStatic
    fun exp(value: Float) = kotlin.math.exp(value)
    @JvmStatic
    fun lerp(start: Float, end: Float, t: Float) = start + (end - start) * t
}
