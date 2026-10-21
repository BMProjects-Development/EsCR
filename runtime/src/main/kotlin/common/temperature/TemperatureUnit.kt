package com.algorithmlx.ecr.common.temperature

import java.util.Locale

enum class TemperatureUnit(val serializedName: String, val symbol: String) {
    CELSIUS("celsius", "°C"),
    FAHRENHEIT("fahrenheit", "°F"),
    KELVIN("kelvin", "K"),
    RANKINE("rankine", "°R");

    fun fromCelsius(celsius: Double): Double = when (this) {
        CELSIUS -> celsius
        FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
        KELVIN -> celsius + 273.15
        RANKINE -> (celsius + 273.15) * 9.0 / 5.0
    }

    fun formatCelsius(celsius: Double, decimals: Int = 1): String = String.format(Locale.ROOT, "%.${decimals.coerceIn(0, 3)}f %s", fromCelsius(celsius), symbol)

    fun next(): TemperatureUnit = entries[(ordinal + 1) % entries.size]

    companion object {
        fun fromSerializedName(name: String): TemperatureUnit = entries.firstOrNull { it.serializedName.equals(name, ignoreCase = true) } ?: CELSIUS
    }
}
