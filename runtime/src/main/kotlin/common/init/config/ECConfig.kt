package com.algorithmlx.ecr.common.init.config

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.config.JsonComment
import com.algorithmlx.ecr.api.config.JsonDefaults
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntityType
import kotlin.random.Random

@JsonComment([
    "Hello! This is $ModId's config.",
    "You can configure mod it however you like. If it is possible.",
    "The config supports both single-line and multi-line comments, similar to those in compiled languages.",
    "If config broken, it backups with current time.",
    "Comments are not cleared when the mod is restarted."
], multiline = true)
@JsonDefaults
@Serializable
data class ECConfig(
    @SerialName("disabled_researches")
    val disabledResearches: List<String> = emptyList(),
    @SerialName("research_book") val researchBook: ResearchBookConfig = ResearchBookConfig(),
    val multiblocks: MultiblockDataConfig = MultiblockDataConfig(),
    @SerialName("cold_distiller") val coldDistillerConfig: ColdDistillerConfig = ColdDistillerConfig(),
    @SerialName("matrix_destructor") val matrixDestructor: MatrixDestructorConfig = MatrixDestructorConfig(),
    @SerialName("magical_teleporter") val magicalTeleporter: MagicalTeleporterConfig = MagicalTeleporterConfig(),
    @SerialName("enrichment_chamber") val enrichmentChamber: EnrichmentChamberConfig = EnrichmentChamberConfig(),
    @SerialName("heat_generator") val heatGenerator: HeatGeneratorConfig = HeatGeneratorConfig(),
    @SerialName("magic_defense") val magicDefense: List<MagicDefenseEntry> = listOf(
        MagicDefenseEntry("minecraft:allay", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:blaze", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:breeze", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:breeze_wind_charge", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:dragon_fireball", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:elder_guardian", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:ender_dragon", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:evoker", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:guardian", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:illusioner", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:iron_golem", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:magma_cube", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:shulker", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:shulker_bullet", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:snow_golem", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:stray", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:vex", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:warden", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:wind_charge", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:witch", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:wither", listOf(MagicDefenseIgnoreFunction)),
        MagicDefenseEntry("minecraft:wither_skull", listOf(MagicDefenseIgnoreFunction)),
    )
) {
    fun magicDefense(type: EntityType<*>): MagicDefenseEntry? = magicDefense.firstOrNull { it.matches(type) }

    companion object {
        @JvmStatic
        lateinit var instance: ECConfig

        @JvmStatic
        val current: ECConfig
            get() = if (::instance.isInitialized) instance else ECConfig()
    }
}

@JsonDefaults
@Serializable
data class MultiblockDataConfig(
    @JsonComment([
        "IDs registered as custom JSON-only multiblocks in data/<namespace>/multiblocks/.",
        "An ID without a namespace uses the $ModId namespace. Every listed ID must have a JSON file.",
        "Changing this list requires a game/server restart because Minecraft registries are frozen after startup."
    ])
    @SerialName("custom_ids")
    val customIds: List<String> = emptyList(),
    @JsonComment([
        "IDs registered as custom JSON-only assembled multiblocks in data/<namespace>/assembled_multiblocks/.",
        "An ID without a namespace uses the $ModId namespace. Every listed ID must have a JSON file.",
        "Changing this list requires a game/server restart because Minecraft registries are frozen after startup."
    ])
    @SerialName("custom_assembled_ids")
    val customAssembledIds: List<String> = emptyList()
) {
    fun customMultiblockRegistryIds(): Set<Identifier> =
        parseRegistryIds(customIds, "custom multiblock")

    fun customAssembledRegistryIds(): Set<Identifier> =
        parseRegistryIds(customAssembledIds, "custom assembled multiblock")

    private fun parseRegistryIds(values: Collection<String>, kind: String): Set<Identifier> =
        values.mapTo(linkedSetOf()) { value ->
            val normalized = value.trim()
            require(normalized.isNotEmpty()) { "Blank $kind ID in config" }
            val namespaced = if (':' in normalized) normalized else "$ModId:$normalized"
            Identifier.tryParse(namespaced) ?: error("Invalid $kind identifier '$value'")
        }
}

@JsonDefaults
@Serializable
data class ResearchBookConfig(
    val space: ResearchBookSpaceConfig = ResearchBookSpaceConfig(),
    val graph: ResearchBookGraphConfig = ResearchBookGraphConfig()
)

@JsonDefaults
@Serializable
data class ResearchBookSpaceConfig(
    @SerialName("parallax_strength") val parallaxStrength: Float = 1F,
    @SerialName("star_density") val starDensity: Float = 1F,
    @SerialName("star_size") val starSize: Float = 1F
)

@JsonDefaults
@Serializable
data class ResearchBookGraphConfig(
    @SerialName("zoom_step") val zoomStep: Float = 0.12F,
    @SerialName("available_blink_seconds") val availableBlinkSeconds: Double = 3.0
)

@JsonDefaults
@Serializable
data class ColdDistillerConfig(
    @SerialName("ice_block_radius")
    val iceBlockRadius: Int = 3,
    @SerialName("min_ice_blocks")
    val minIceBlocks: Int = 2,
    @SerialName("min_mru")
    val minMruPerSecond: Int = 1,
    @SerialName("max_mru")
    val maxMruPerSecond: Int = 16,
    @SerialName("destroy_ice")
    val destroyIce: DestroyIceConfig = DestroyIceConfig(),
    @SerialName("balance_produced") val balanceProduced: Double = 0.0
) {
    init {
        require(balanceProduced.isFinite() && balanceProduced in 0.0..2.0) { "Cold Distiller balance must be between zero and two" }
    }
}

@JsonDefaults
@Serializable
data class MatrixDestructorConfig(@SerialName("balance_produced") val balanceProduced: Double = 1.0) {
    init {
        require(balanceProduced.isFinite() && balanceProduced in 0.0..2.0) { "Matrix Destructor balance must be between zero and two" }
    }
}

@JsonDefaults
@Serializable
data class DestroyIceConfig(
    val enabled: Boolean = true,
    val chance: Chance = Chance(7, 10),
    val time: Int = 100
)

@JsonDefaults
@Serializable
data class Chance(
    val min: Int,
    val max: Int
) {
    init {
        require(-100 <= max && max <= 100)
        require(min in (-100 .. max))
    }

    fun roll(): Int = Random.nextInt(max)

    fun isRolled(): Boolean = min >= this.roll()
}

@JsonDefaults
@Serializable
data class MagicalTeleporterConfig(
    @SerialName("mru_usage") val mruUsage: Int = 500,
    @SerialName("ticks_required") val ticksRequired: Int = 250
)

@JsonDefaults
@Serializable
data class EnrichmentChamberConfig(
    @SerialName("controller_capacity") val controllerCapacity: Int = 60000,
    @SerialName("holder_capacity") val holderCapacity: Int = 100000
)

@JsonDefaults
@Serializable
data class HeatGeneratorConfig(val capacity: Int = 10000, @SerialName("ultra_capacity") val ultraCapacity: Int = 100000, @SerialName("default_generation") val defaultGeneration: Int = 1, @SerialName("heat_block_generation") val heatBlockGeneration: Map<String, Int> = mapOf("minecraft:netherrack" to 4, "minecraft:fire" to 8, "minecraft:magma_block" to 12, "minecraft:lava" to 16), val ultra: UltraHeatGeneratorConfig = UltraHeatGeneratorConfig(), @SerialName("default_balance") val defaultBalance: Double = -1.0) {
    init {
        require(capacity > 0) { "Heat Generator capacity must be positive" }
        require(ultraCapacity > 0) { "Ultra Heat Generator capacity must be positive" }
        require(defaultGeneration >= 0) { "Heat Generator default generation cannot be negative" }
        require(heatBlockGeneration.values.all { it >= 0 }) { "Heat block generation cannot be negative" }
        require(defaultBalance.isFinite() && (defaultBalance == -1.0 || defaultBalance in 0.0..2.0)) { "Heat Generator balance must be between zero and two, or minus one for a random balance" }
    }
}

@JsonDefaults
@Serializable
data class UltraHeatGeneratorConfig(@SerialName("burn_speed") val burnSpeed: Double = 1.25, @SerialName("heating_speed") val heatingSpeed: Double = 0.75, @SerialName("heating_slowdown_temperature_celsius") val heatingSlowdownTemperatureCelsius: Double = 200.0, @SerialName("maximum_temperature_celsius") val maximumTemperatureCelsius: Double = 10000.0, @SerialName("cooling_speed") val coolingSpeed: Double = 0.25, @SerialName("generation_temperature_celsius") val generationTemperatureCelsius: Double = 100.0, @SerialName("world_effects") val worldEffects: UltraHeatWorldEffectsConfig = UltraHeatWorldEffectsConfig()) {
    init {
        require(burnSpeed.isFinite() && burnSpeed > 0.0) { "Ultra Heat Generator burn speed must be finite and positive" }
        require(heatingSpeed.isFinite() && heatingSpeed > 0.0) { "Ultra Heat Generator heating speed must be finite and positive" }
        require(heatingSlowdownTemperatureCelsius.isFinite() && heatingSlowdownTemperatureCelsius > 0.0) { "Ultra Heat Generator heating slowdown temperature must be finite and positive" }
        require(maximumTemperatureCelsius.isFinite() && maximumTemperatureCelsius > heatingSlowdownTemperatureCelsius) { "Ultra Heat Generator maximum temperature must be finite and greater than its heating slowdown temperature" }
        require(coolingSpeed.isFinite() && coolingSpeed > 0.0) { "Ultra Heat Generator cooling speed must be finite and positive" }
        require(generationTemperatureCelsius.isFinite() && generationTemperatureCelsius >= 0.0 && generationTemperatureCelsius <= maximumTemperatureCelsius) { "Ultra Heat Generator generation temperature must be finite and between zero and its maximum temperature" }
    }
}

@JsonDefaults
@Serializable
data class HeatBlockTransitionConfig(val target: String, @SerialName("temperature_celsius") val temperatureCelsius: Double = 100.0, @SerialName("cooling_celsius") val coolingCelsius: Double = 0.0) {
    init {
        require(Identifier.tryParse(target) != null) { "Invalid Heat Generator transition target '$target'" }
        require(temperatureCelsius.isFinite() && temperatureCelsius >= 0.0) { "Heat Generator transition temperature must be finite and non-negative" }
        require(coolingCelsius.isFinite() && coolingCelsius >= 0.0) { "Heat Generator transition cooling must be finite and non-negative" }
    }
}

@JsonDefaults
@Serializable
data class HeatPlayerIgnitionConfig(@SerialName("temperature_celsius") val temperatureCelsius: Double, val area: Int) {
    init {
        require(temperatureCelsius.isFinite() && temperatureCelsius >= 0.0) { "Heat Generator player ignition temperature must be finite and non-negative" }
        require(area > 0) { "Heat Generator player ignition area must be positive" }
    }
}

@JsonDefaults
@Serializable
data class HeatSurfaceFireConfig(
    @SerialName("temperature_celsius") val temperatureCelsius: Double = 1800.0,
    val area: Int = 8,
    @SerialName("vertical_radius") val verticalRadius: Int = 4,
    @SerialName("interval_ticks") val intervalTicks: Int = 20
) {
    init {
        require(temperatureCelsius.isFinite() && temperatureCelsius >= 0.0) { "Heat Generator surface fire temperature must be finite and non-negative" }
        require(area > 0) { "Heat Generator surface fire area must be positive" }
        require(verticalRadius >= 0) { "Heat Generator surface fire vertical radius cannot be negative" }
        require(intervalTicks > 0) { "Heat Generator surface fire interval must be positive" }
    }
}

@JsonDefaults
@Serializable
data class UltraHeatWorldEffectsConfig(
    val enabled: Boolean = true,
    @SerialName("temperature_celsius") val temperatureCelsius: Double = 700.0,
    @SerialName("interval_ticks") val intervalTicks: Int = 100,
    val radius: Int = 2,
    @SerialName("transition_vertical_radius") val transitionVerticalRadius: Int = 1,
    @SerialName("instant_temperature_celsius") val instantTemperatureCelsius: Double = 1800.0,
    @SerialName("default_transition") val defaultTransition: String = "minecraft:magma_block",
    @JsonComment([
        "Replacement for flammable blocks not listed in block_transitions.",
        "Use minecraft:air to make them burn away. Explicit block_transitions take priority."
    ])
    @SerialName("flammable_transition") val flammableTransition: String = "minecraft:air",
    @SerialName("block_transitions") val blockTransitions: Map<String, HeatBlockTransitionConfig> = defaultHeatBlockTransitions(),
    @SerialName("surface_fire") val surfaceFire: HeatSurfaceFireConfig = HeatSurfaceFireConfig(),
    @SerialName("player_ignition") val playerIgnition: List<HeatPlayerIgnitionConfig> = defaultHeatPlayerIgnition(),
    @SerialName("player_fire_seconds") val playerFireSeconds: Double = 5.0,
    @SerialName("player_vertical_range") val playerVerticalRange: Double = 3.0,
    @SerialName("player_ignite_interval_ticks") val playerIgniteIntervalTicks: Int = 20
) {
    init {
        require(temperatureCelsius.isFinite() && temperatureCelsius >= 0.0) { "Ultra Heat Generator world effect temperature must be finite and non-negative" }
        require(intervalTicks > 0) { "Ultra Heat Generator world effect interval must be positive" }
        require(radius >= 0) { "Ultra Heat Generator world effect radius cannot be negative" }
        require(transitionVerticalRadius >= 0) { "Ultra Heat Generator transition vertical radius cannot be negative" }
        require(instantTemperatureCelsius.isFinite() && instantTemperatureCelsius >= 0.0) { "Ultra Heat Generator instant temperature must be finite and non-negative" }
        require(Identifier.tryParse(defaultTransition) != null) { "Invalid Heat Generator default transition '$defaultTransition'" }
        require(Identifier.tryParse(flammableTransition) != null) { "Invalid Heat Generator flammable transition '$flammableTransition'" }
        require(blockTransitions.keys.all { Identifier.tryParse(it) != null }) { "Invalid Heat Generator transition source" }
        require(playerIgnition.isNotEmpty()) { "Heat Generator player ignition list cannot be empty" }
        require(playerFireSeconds.isFinite() && playerFireSeconds > 0.0) { "Heat Generator player fire seconds must be finite and positive" }
        require(playerVerticalRange.isFinite() && playerVerticalRange > 0.0) { "Heat Generator player vertical range must be finite and positive" }
        require(playerIgniteIntervalTicks > 0) { "Heat Generator player ignition interval must be positive" }
    }

    fun playerIgnitionArea(temperatureCelsius: Double): Int? {
        if (!temperatureCelsius.isFinite()) return null
        val highestTemperature = this.playerIgnition.maxOf { it.temperatureCelsius }
        return this.playerIgnition.filter { temperatureCelsius >= it.temperatureCelsius && (it.temperatureCelsius < highestTemperature || temperatureCelsius > it.temperatureCelsius) }.maxByOrNull { it.temperatureCelsius }?.area
    }

    fun blockTransitionFor(sourceId: String): HeatBlockTransitionConfig? {
        val normalizedSourceId = Identifier.tryParse(sourceId)?.toString() ?: return null
        return this.blockTransitions[normalizedSourceId]
            ?: this.blockTransitions.entries.firstOrNull { Identifier.tryParse(it.key)?.toString() == normalizedSourceId }?.value
    }
}

private fun defaultHeatBlockTransitions(): Map<String, HeatBlockTransitionConfig> = linkedMapOf("minecraft:blue_ice" to HeatBlockTransitionConfig("minecraft:packed_ice"), "minecraft:packed_ice" to HeatBlockTransitionConfig("minecraft:ice"), "minecraft:ice" to HeatBlockTransitionConfig("minecraft:water"), "minecraft:water" to HeatBlockTransitionConfig("minecraft:obsidian", coolingCelsius = 100.0), "minecraft:obsidian" to HeatBlockTransitionConfig("minecraft:lava", 700.0), "minecraft:magma_block" to HeatBlockTransitionConfig("minecraft:lava", 700.0))

private fun defaultHeatPlayerIgnition(): List<HeatPlayerIgnitionConfig> = listOf(HeatPlayerIgnitionConfig(800.0, 3), HeatPlayerIgnitionConfig(1300.0, 5), HeatPlayerIgnitionConfig(1800.0, 8))
