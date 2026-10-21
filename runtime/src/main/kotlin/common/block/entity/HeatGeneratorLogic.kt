package com.algorithmlx.ecr.common.block.entity

import kotlin.math.floor

object HeatGeneratorLogic {
    const val LOW_HEAT_THRESHOLD = 1000.0
    const val HIGH_HEAT_THRESHOLD = 10000.0

    data class UltraTick(val nextTemperatureCelsius: Double, val fuelCost: Double, val generation: Int)
    data class IntervalTick(val nextElapsedTicks: Int, val triggered: Boolean)

    fun ultraTick(temperatureCelsius: Double, burnSpeed: Double, heatingSpeed: Double = 0.75, generationTemperatureCelsius: Double = 100.0, heatingSlowdownTemperatureCelsius: Double = 200.0, maximumTemperatureCelsius: Double = 10000.0): UltraTick {
        val slowdownTemperature = heatingSlowdownTemperatureCelsius.takeIf { it.isFinite() && it > 0.0 } ?: 200.0
        val maximumTemperature = maximumTemperatureCelsius.takeIf { it.isFinite() && it > slowdownTemperature } ?: 10000.0
        val heat = temperatureCelsius.takeIf { it.isFinite() }?.coerceIn(0.0, maximumTemperature) ?: 0.0
        val heatFactor = when {
            heat >= maximumTemperature -> 0.0
            heat < slowdownTemperature -> 0.1 + heat / slowdownTemperature * 0.9
            else -> (maximumTemperature - heat) / (maximumTemperature - slowdownTemperature)
        }
        val fuelCost = burnSpeed.takeIf { it.isFinite() && it > 0.0 } ?: 1.25
        val speed = heatingSpeed.takeIf { it.isFinite() && it > 0.0 } ?: 0.75
        val generationTemperature = generationTemperatureCelsius.takeIf { it.isFinite() && it >= 0.0 } ?: 100.0
        val nextHeat = (heat + heatFactor * speed).coerceAtMost(maximumTemperature)
        val generated = when {
            nextHeat < generationTemperature -> 0.0
            nextHeat < LOW_HEAT_THRESHOLD -> nextHeat / 100.0
            nextHeat > HIGH_HEAT_THRESHOLD -> 80.0 + nextHeat / 1000.0
            else -> nextHeat / 124.0
        }

        return UltraTick(nextHeat, fuelCost, floor(generated).coerceIn(0.0, Int.MAX_VALUE.toDouble()).toInt())
    }

    fun coolDown(temperatureCelsius: Double, coolingSpeed: Double): Double {
        val heat = temperatureCelsius.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0
        val speed = coolingSpeed.takeIf { it.isFinite() && it > 0.0 } ?: 0.25
        return (heat - speed).coerceAtLeast(0.0)
    }

    fun advanceInterval(elapsedTicks: Int, intervalTicks: Int): IntervalTick {
        val interval = intervalTicks.coerceAtLeast(1)
        val elapsed = elapsedTicks.coerceAtLeast(0)
        return if (elapsed >= interval - 1) IntervalTick(0, true) else IntervalTick(elapsed + 1, false)
    }

    fun centeredRange(size: Int): IntRange {
        val normalizedSize = size.coerceAtLeast(1)
        val minimum = -(normalizedSize / 2)
        return minimum..<minimum + normalizedSize
    }

    fun nearestVerticalOffsets(radius: Int): Sequence<Int> = sequence {
        yield(0)
        for (distance in 1..radius.coerceAtLeast(0)) {
            yield(-distance)
            yield(distance)
        }
    }

    fun unconfiguredTransitionTarget(isFire: Boolean, ignitedByLava: Boolean, flammableTarget: String, defaultTarget: String): String? = when {
        isFire -> null
        ignitedByLava -> flammableTarget
        else -> defaultTarget
    }

    fun dataLow(value: Int): Int = value and 65535

    fun dataHigh(value: Int): Int = (value ushr 16) and 65535

    fun combineData(low: Int, high: Int): Int = (low and 65535) or ((high and 65535) shl 16)
}
