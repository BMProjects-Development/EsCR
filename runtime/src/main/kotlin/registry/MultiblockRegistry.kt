package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.multiblock.Multiblock
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDefinition
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.common.init.config.ECConfig
import com.algorithmlx.ecr.common.multiblocks.AirCrystal
import com.algorithmlx.ecr.common.multiblocks.EarthCrystal
import com.algorithmlx.ecr.common.multiblocks.EnrichmentChamber
import com.algorithmlx.ecr.common.multiblocks.FlameCrystal
import com.algorithmlx.ecr.common.multiblocks.LightningCollector
import com.algorithmlx.ecr.common.multiblocks.MagicalTeleporter
import com.algorithmlx.ecr.common.multiblocks.MithrilineFurnaceMultiblock
import com.algorithmlx.ecr.common.multiblocks.RayTowerMultiblock
import com.algorithmlx.ecr.common.multiblocks.SoulStoneMultiblock
import com.algorithmlx.ecr.common.multiblocks.WaterCrystal
import net.minecraft.resources.Identifier

object MultiblockRegistry : RegistrationHandler(ModId) {
    private val codeMithrilineFurnace =
        registerNoEntry(ECRModIDs.MITHRILINE_FURNACE, ECRegistries.MULTIBLOCK) { MithrilineFurnaceMultiblock }
    private val codeSoulStone =
        registerNoEntry(ECRModIDs.SOUL_STONE, ECRegistries.MULTIBLOCK) { SoulStoneMultiblock }
    private val codeFlameCrystal =
        registerNoEntry(ECRModIDs.FLAME_CRYSTAL, ECRegistries.MULTIBLOCK) { FlameCrystal }
    private val codeWaterCrystal =
        registerNoEntry(ECRModIDs.WATER_CRYSTAL, ECRegistries.MULTIBLOCK) { WaterCrystal }
    private val codeEarthCrystal =
        registerNoEntry(ECRModIDs.EARTH_CRYSTAL, ECRegistries.MULTIBLOCK) { EarthCrystal }
    private val codeAirCrystal =
        registerNoEntry(ECRModIDs.AIR_CRYSTAL, ECRegistries.MULTIBLOCK) { AirCrystal }
    private val codeLightningCollector =
        registerNoEntry(ECRModIDs.LIGHTNING_COLLECTOR, ECRegistries.MULTIBLOCK) { LightningCollector }
    private val codeEnrichmentChamber =
        registerNoEntry(ECRModIDs.ENRICHMENT_CHAMBER, ECRegistries.MULTIBLOCK) { EnrichmentChamber }
    private val codeRayTower =
        registerNoEntry(ECRModIDs.RAY_TOWER, ECRegistries.ASSEMBLED_MULTIBLOCK) { RayTowerMultiblock }
    private val codeMagicalTeleporter =
        registerNoEntry(ECRModIDs.MAGICAL_TELEPORTER, ECRegistries.MULTIBLOCK) { MagicalTeleporter }

    init {
        registerConfiguredMultiblocks(
            ECConfig.current.multiblocks.customMultiblockRegistryIds(),
            setOf(
                ECRModIDs.MITHRILINE_FURNACE,
                ECRModIDs.SOUL_STONE,
                ECRModIDs.FLAME_CRYSTAL,
                ECRModIDs.WATER_CRYSTAL,
                ECRModIDs.EARTH_CRYSTAL,
                ECRModIDs.AIR_CRYSTAL,
                ECRModIDs.LIGHTNING_COLLECTOR,
                ECRModIDs.ENRICHMENT_CHAMBER,
                ECRModIDs.MAGICAL_TELEPORTER,
            ).mapTo(linkedSetOf(), String::ecRL),
        )
        registerConfiguredAssembledMultiblocks(
            ECConfig.current.multiblocks.customAssembledRegistryIds(),
            setOf(ECRModIDs.RAY_TOWER.ecRL),
        )
    }

    val mithrilineFurnace: Multiblock
        get() = MultiblockDefinitions[ECRModIDs.MITHRILINE_FURNACE.ecRL] ?: codeMithrilineFurnace.get()
    val soulStone: Multiblock
        get() = MultiblockDefinitions[ECRModIDs.SOUL_STONE.ecRL] ?: codeSoulStone.get()
    val flameCrystal: Multiblock
        get() = MultiblockDefinitions[ECRModIDs.FLAME_CRYSTAL.ecRL] ?: codeFlameCrystal.get()
    val waterCrystal: Multiblock
        get() = MultiblockDefinitions[ECRModIDs.WATER_CRYSTAL.ecRL] ?: codeWaterCrystal.get()
    val earthCrystal: Multiblock
        get() = MultiblockDefinitions[ECRModIDs.EARTH_CRYSTAL.ecRL] ?: codeEarthCrystal.get()
    val airCrystal: Multiblock
        get() = MultiblockDefinitions[ECRModIDs.AIR_CRYSTAL.ecRL] ?: codeAirCrystal.get()
    val lightningCollector: Multiblock
        get() = MultiblockDefinitions[ECRModIDs.LIGHTNING_COLLECTOR.ecRL] ?: codeLightningCollector.get()
    val enrichmentChamber: Multiblock
        get() = MultiblockDefinitions[ECRModIDs.ENRICHMENT_CHAMBER.ecRL] ?: codeEnrichmentChamber.get()
    val rayTower: AssembledMultiblockDefinition
        get() = MultiblockDefinitions.assembled(ECRModIDs.RAY_TOWER.ecRL) ?: codeRayTower.get()
    val magicalTeleporter: Multiblock
        get() = MultiblockDefinitions[ECRModIDs.MAGICAL_TELEPORTER.ecRL] ?: codeMagicalTeleporter.get()
    private fun registerConfiguredMultiblocks(ids: Set<Identifier>, occupiedIds: Set<Identifier>) {
        ids.forEach { id ->
            check(id !in occupiedIds) {
                "Configured custom multiblock $id is already registered; remove it from custom_ids " +
                    "and use its JSON file as an override"
            }
            registerNoEntry(id, ECRegistries.MULTIBLOCK) { Multiblock.jsonOnly() }
        }
    }

    private fun registerConfiguredAssembledMultiblocks(ids: Set<Identifier>, occupiedIds: Set<Identifier>) {
        ids.forEach { id ->
            check(id !in occupiedIds) {
                "Configured custom assembled multiblock $id is already registered; remove it from " +
                    "custom_assembled_ids and use its JSON file as an override"
            }
            registerNoEntry(id, ECRegistries.ASSEMBLED_MULTIBLOCK) {
                AssembledMultiblockDefinition.jsonOnly(id)
            }
        }
    }
}
