package com.algorithmlx.ecr.init.config

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.config.JsonComment
import com.algorithmlx.ecr.api.config.JsonDefaults
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.EntityType
import kotlin.random.Random
import com.algorithmlx.ecr.common.magic.MagicDefenseContext
import com.algorithmlx.ecr.common.magic.MagicDefenseResult
import net.minecraft.core.registries.BuiltInRegistries

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
    @SerialName("sun_ray_absorber") val sunRayAbsorber: SunRayAbsorberConfig = SunRayAbsorberConfig(),
    @SerialName("matrix_destructor") val matrixDestructor: MatrixDestructorConfig = MatrixDestructorConfig(),
    @SerialName("magical_teleporter") val magicalTeleporter: MagicalTeleporterConfig = MagicalTeleporterConfig(),
    @SerialName("enrichment_chamber") val enrichmentChamber: EnrichmentChamberConfig = EnrichmentChamberConfig(),
    @SerialName("heat_generator") val heatGenerator: HeatGeneratorConfig = HeatGeneratorConfig(),
    @SerialName("natural_furnace") val naturalFurnace: NaturalFurnaceConfig = NaturalFurnaceConfig(),
    @SerialName("ender_generator") val enderGenerator: EnderGeneratorConfig = EnderGeneratorConfig(),
    @SerialName("monster_holding_device") val monsterHolding: MonsterHoldingConfig = MonsterHoldingConfig(),
    @SerialName("animal_stations") val animalStations: AnimalStationConfig = AnimalStationConfig(),
    @SerialName("magical_hopper") val magicalHopper: MagicalHopperConfig = MagicalHopperConfig(),
    val magmatic: MagmaticConfig = MagmaticConfig(),
    @SerialName("inventory_manager") val inventoryManager: InventoryManagerConfig = InventoryManagerConfig(),
    @SerialName("magical_mirror") val magicalMirror: MagicalMirrorConfig = MagicalMirrorConfig(),
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
        MagicDefenseEntry("minecraft:wither_skull", listOf(MagicDefenseIgnoreFunction))
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
    fun customMultiblockRegistryIds(): Set<Identifier> = parseRegistryIds(customIds, "custom multiblock")

    fun customAssembledRegistryIds(): Set<Identifier> = parseRegistryIds(customAssembledIds, "custom assembled multiblock")

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
data class ResearchBookConfig(val space: ResearchBookSpaceConfig = ResearchBookSpaceConfig(), val graph: ResearchBookGraphConfig = ResearchBookGraphConfig())

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
data class SunRayAbsorberConfig(
    val capacity: Int = 100000,
    @SerialName("generation_per_tick") val generationPerTick: Int = 500,
    @SerialName("balance_produced") val balanceProduced: Double = 2.0,
    @SerialName("beam_chance") val beamChance: Double = 0.025,
    @SerialName("beam_duration") val beamDuration: Int = 20,
    @SerialName("requires_unobstructed_sky") val requiresUnobstructedSky: Boolean = true,
    @SerialName("requires_midday") val requiresMidday: Boolean = true,
    @SerialName("midday_start") val middayStart: Long = 5000,
    @SerialName("midday_end") val middayEnd: Long = 7000,
    @SerialName("ignore_rain") val ignoreRain: Boolean = false
) {
    init {
        require(capacity > 0) { "Sun Ray Absorber capacity must be positive" }
        require(generationPerTick >= 0) { "Sun Ray Absorber generation cannot be negative" }
        require(balanceProduced.isFinite() && balanceProduced in 0.0..2.0) { "Sun Ray Absorber balance must be between zero and two" }
        require(beamChance.isFinite() && beamChance in 0.0..1.0) { "Sun Ray Absorber beam chance must be between zero and one" }
        require(beamDuration > 0) { "Sun Ray Absorber beam duration must be positive" }
        require(middayStart in 0..<24000 && middayEnd in middayStart..<24000) { "Sun Ray Absorber midday range must fit within a Minecraft day" }
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
data class DestroyIceConfig(val enabled: Boolean = true, val chance: Chance = Chance(7, 10), val time: Int = 100)

@JsonDefaults
@Serializable
data class Chance(val min: Int, val max: Int) {
    init {
        require(-100 <= max && max <= 100)
        require(min in (-100 .. max))
    }

    fun roll(): Int = Random.nextInt(max)

    fun isRolled(): Boolean = min >= this.roll()
}

@JsonDefaults
@Serializable
data class MagicalTeleporterConfig(@SerialName("mru_usage") val mruUsage: Int = 500, @SerialName("ticks_required") val ticksRequired: Int = 250)

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
    @JsonComment(["Replacement for flammable blocks not listed in block_transitions.", "Use minecraft:air to make them burn away. Explicit block_transitions take priority."])
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

@Serializable
data class NaturalFurnaceConfig(
    val capacity: Int = 10000, @SerialName("ultra_capacity") val ultraCapacity: Int = 100000,
    val radius: Int = 8, @SerialName("ultra_radius") val ultraRadius: Int = 16,
    @SerialName("ultra_height") val ultraHeight: Int = 32, @SerialName("default_balance") val defaultBalance: Double = -1.0,
    @SerialName("planting_interval") val plantingInterval: Int = 80,
    @SerialName("flower_ticks") val flowerTicks: Int = 300, @SerialName("sapling_ticks") val saplingTicks: Int = 450,
    @SerialName("grass_ticks") val grassTicks: Int = 75, val generation: Int = 10,
    val ultra: NaturalFurnaceFuelConfig = NaturalFurnaceFuelConfig()
) {
    init {
        require(capacity > 0 && ultraCapacity >= capacity)
        require(radius >= 0 && radius < Int.MAX_VALUE / 2 && ultraRadius >= 0 && ultraRadius < Int.MAX_VALUE / 2 && ultraHeight >= 0)
        require(defaultBalance.isFinite() && (defaultBalance == -1.0 || defaultBalance in 0.0..2.0))
        require(plantingInterval > 0 && flowerTicks > 0 && saplingTicks > 0 && grassTicks > 0 && generation > 0)
    }
}

@Serializable
data class NaturalFurnaceFuelConfig(
    @SerialName("flower_ticks") val flowerTicks: Int = 300, @SerialName("flower_generation") val flowerGeneration: Int = 60,
    @SerialName("leaves_ticks") val leavesTicks: Int = 150, @SerialName("leaves_generation") val leavesGeneration: Int = 70,
    @SerialName("grass_ticks") val grassTicks: Int = 50, @SerialName("grass_generation") val grassGeneration: Int = 20,
    @SerialName("log_ticks") val logTicks: Int = 1200, @SerialName("log_generation") val logGeneration: Int = 100
) {
    init { require(listOf(flowerTicks, flowerGeneration, leavesTicks, leavesGeneration, grassTicks, grassGeneration, logTicks, logGeneration).all { it > 0 }) }
}

@Serializable
data class EnderGeneratorConfig(
    val capacity: Int = 10000, val radius: Int = 8, val generation: Int = 500, val damage: Float = 1F,
    @SerialName("default_balance") val defaultBalance: Double = -1.0,
    val entities: Set<String> = setOf("minecraft:enderman")
) {
    init {
        require(capacity > 0 && radius > 0 && generation > 0 && damage.isFinite() && damage > 0F)
        require(defaultBalance.isFinite() && (defaultBalance == -1.0 || defaultBalance in 0.0..2.0))
        require(entities.all { Identifier.tryParse(it) != null })
    }
}

@Serializable
data class MonsterHoldingConfig(val capacity: Int = 5000, val radius: Double = 12.0, val boundary: Double = 3.0, val cost: Int = 1, val attraction: Double = 1.1) {
    init {
        require(capacity > 0 && radius.isFinite() && radius > 0.0)
        require(boundary.isFinite() && boundary > 0.0 && boundary <= radius && cost in 1..capacity && attraction.isFinite() && attraction > 1.0)
    }
}

@Serializable
data class AnimalStationConfig(val radius: Int = 12, @SerialName("feeding_radius") val feedingRadius: Double = 3.0, val fortune: Int = 2) {
    init { require(radius > 0 && feedingRadius.isFinite() && feedingRadius > 0.0 && fortune in 0..255) }
}

@Serializable
data class MagicalHopperConfig(val radius: Int = 3, val interval: Int = 20) {
    init { require(radius >= 0 && interval > 0) }
}

@Serializable
data class MagmaticConfig(
    val capacity: Int = 5000, @SerialName("furnace_cost") val furnaceCost: Int = 25, @SerialName("furnace_ticks") val furnaceTicks: Int = 400,
    @SerialName("ore_cost") val oreCost: Int = 50, @SerialName("ore_ticks") val oreTicks: Int = 400,
    @SerialName("alloy_cost") val alloyCost: Int = 30, @SerialName("alloy_ticks") val alloyTicks: Int = 40,
    @SerialName("quadrupling_chance") val quadruplingChance: Float = 0.33F,
    @SerialName("lava_capacity") val lavaCapacity: Int = 8000, @SerialName("lava_consumption_chance") val lavaConsumptionChance: Float = 0.1F,
    @SerialName("ore_overrides") val oreOverrides: List<MagmaticOreConfig> = listOf(
        MagmaticOreConfig("#minecraft:redstone_ores", "minecraft:redstone", 8),
        MagmaticOreConfig("#minecraft:lapis_ores", "minecraft:lapis_lazuli", 16)
    )
) {
    init {
        require(listOf(capacity, furnaceCost, furnaceTicks, oreCost, oreTicks, alloyCost, alloyTicks).all { it > 0 })
        require(capacity >= maxOf(furnaceCost, oreCost, alloyCost))
        require(quadruplingChance.isFinite() && quadruplingChance in 0F..1F)
        require(lavaCapacity >= 1000 && lavaConsumptionChance.isFinite() && lavaConsumptionChance in 0F..1F)
        require(oreOverrides.map { it.input }.distinct().size == oreOverrides.size)
    }
}

@Serializable
data class MagmaticOreConfig(val input: String, val output: String, val alloys: Int = 1) {
    init { require(Identifier.tryParse(input.removePrefix("#")) != null && Identifier.tryParse(output) != null && alloys in 1..32) }
}

@JsonDefaults
@Serializable
data class InventoryManagerConfig(
    @SerialName("core_capacity") val coreCapacity: Int = 5000,
    @SerialName("screen_insert_cost") val screenInsertCost: Int = 500,
    @SerialName("screen_extract_cost") val screenExtractCost: Int = 10,
    @SerialName("operation_ticks") val operationTicks: Int = 20,
    @SerialName("crafting_steps_per_tick") val craftingStepsPerTick: Int = 256,
    @SerialName("max_queued_crafts") val maxQueuedCrafts: Int = 4096,
    @SerialName("crafting_base_ticks") val craftingBaseTicks: Int = 10,
    @SerialName("crafting_ticks_per_ingredient") val craftingTicksPerIngredient: Int = 2,
    @SerialName("crafting_mru_per_complexity") val craftingMRUPerComplexity: Int = 1,
    @SerialName("dependency_max_nodes") val dependencyMaxNodes: Int = 256,
    @SerialName("dependency_max_depth") val dependencyMaxDepth: Int = 32,
    @SerialName("dependency_max_crafts") val dependencyMaxCrafts: Int = 4096,
    @SerialName("filter_max_nodes") val filterMaxNodes: Int = 256,
    @SerialName("filter_max_depth") val filterMaxDepth: Int = 8
) {
    init {
        require(coreCapacity >= 0) { "MIM capacity cannot be negative" }
        require(screenInsertCost >= 0 && screenExtractCost >= 0) { "MIM operation costs cannot be negative" }
        require(operationTicks > 0) { "MIM operation interval must be positive" }
        require(craftingStepsPerTick > 0 && maxQueuedCrafts > 0) { "MIM crafting budgets must be positive" }
        require(craftingBaseTicks > 0 && craftingTicksPerIngredient >= 0 && craftingMRUPerComplexity >= 0) { "Invalid MIM crafting costs" }
        require(dependencyMaxNodes > 0 && dependencyMaxDepth > 0 && dependencyMaxCrafts > 0) { "Invalid MIM dependency budgets" }
        require(filterMaxNodes >= 9 && filterMaxDepth >= 1) { "MIM filter budget must fit at least one nine-slot filter" }
    }
}

@JsonDefaults
@Serializable
data class MagicalMirrorConfig(val range: Double = 8.0, @SerialName("transfer_ticks") val transferTicks: Int = 60) {
    init {
        require(range.isFinite() && range >= 0) { "Mirror range must be finite and nonnegative" }
        require(transferTicks > 0) { "Mirror transfer interval must be positive" }
    }
}

// Yo bit... oh, surely swear words arent consideered decent?
// Fuck off with ur so-called of decency. it's my code, so i will do whatever i want with it.
// (c) AlgorithmLX
// Oh fuck, my monitor is dead
// No, it is alive :). HDMI is dead
// Maybe i should scattercomments like that thoughout the code? Why not?

@Serializable
data class MagicDefenseRange(val min: Int, val max: Int) {
    init {
        require(max >= min)
    }

    fun successful(): Boolean = Random.nextInt(max) + 1 >= min
}

@Serializable
data class MagicDefenseEntry(val id: String, val functions: List<MagicDefenseFunction>) {
    fun matches(type: EntityType<*>): Boolean = id == BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()

    init {
        require(!id.contains("enderman") && !id.contains("endermite")) {
            "I understand that you might not want to add shields to Enderman/Endermite, " +
                    "but it is not possible. Please respect the mod's lore. That is my only request as a developer :)."
        }
        require(functions.isNotEmpty()) { "Functions cannot be empty." }
    }
}

@Serializable
sealed interface MagicDefenseFunction {
    val priority: Int get() = 100
    val ignoreDamage: Boolean get() = false

    fun apply(context: MagicDefenseContext, result: MagicDefenseResult): MagicDefenseResult
}

@Serializable
@SerialName("ignore")
data object MagicDefenseIgnoreFunction: MagicDefenseFunction {
    override val priority: Int = 0
    override val ignoreDamage: Boolean = true

    override fun apply(context: MagicDefenseContext, result: MagicDefenseResult): MagicDefenseResult {
        if (result.stopped) return result
        return when (context.enchantmentLevel) {
            0, 1 -> result.stop(0F)
            2 -> result
            else -> result.stop(context.originalDamage * 0.2F)
        }
    }
}

@Serializable
@SerialName("increase")
data class MagicDefenseIncreaseFunction(val count: Float): MagicDefenseFunction {
    override val priority: Int = 200

    override fun apply(context: MagicDefenseContext, result: MagicDefenseResult): MagicDefenseResult {
        if (result.stopped || (context.hasIgnore && context.enchantmentLevel == 2)) return result
        // yea, i'm a bit mad
        return result.continueWith(result.damage.minus(count).coerceAtLeast(0F))
    }
}

@Serializable
@SerialName("random")
data class MagicDefenseRandomFunction(val range: MagicDefenseRange): MagicDefenseFunction {
    override fun apply(context: MagicDefenseContext, result: MagicDefenseResult): MagicDefenseResult {
        if (result.stopped) return result

        return if (range.successful()) result
        else result.stop(0F)
    }
}
