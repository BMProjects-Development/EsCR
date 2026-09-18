package com.algorithmlx.ecr.neoforge.init.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDefinition
import com.algorithmlx.ecr.api.multiblock.Multiblock
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.common.init.config.ECConfig
import com.algorithmlx.ecr.common.multiblocks.*
import com.algorithmlx.ecr.registry.MultiblockRegistry
import net.minecraft.core.Registry
import net.minecraft.resources.Identifier
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredRegister
import kotlin.properties.ReadOnlyProperty

class NeoForgeMultiblockRegistry(bus: IEventBus): MultiblockRegistry {
    private val multiblocks = DeferredRegister.create(ECRegistries.MULTIBLOCK, ModId)
    private val assembled = DeferredRegister.create(ECRegistries.ASSEMBLED_MULTIBLOCK, ModId)

    override val mithrilineFurnace: Multiblock by registerMultiblock(ECRModIDs.MITHRILINE_FURNACE) { MithrilineFurnaceMultiblock }
    override val soulStone: Multiblock by registerMultiblock(ECRModIDs.SOUL_STONE) { SoulStoneMultiblock }
    override val flameCrystal: Multiblock by registerMultiblock(ECRModIDs.FLAME_CRYSTAL) { FlameCrystal }
    override val waterCrystal: Multiblock by registerMultiblock(ECRModIDs.WATER_CRYSTAL) { WaterCrystal }
    override val earthCrystal: Multiblock by registerMultiblock(ECRModIDs.EARTH_CRYSTAL) { EarthCrystal }
    override val airCrystal: Multiblock by registerMultiblock(ECRModIDs.AIR_CRYSTAL) { AirCrystal }
    override val lightningCollector: Multiblock by registerMultiblock(ECRModIDs.LIGHTNING_COLLECTOR) { LightningCollector }
    override val enrichmentChamber: Multiblock by registerMultiblock(ECRModIDs.ENRICHMENT_CHAMBER) { EnrichmentChamber }
    override val rayTower: AssembledMultiblockDefinition by registerAssembled(ECRModIDs.RAY_TOWER) { RayTowerMultiblock }
    override val magicalTeleporter: Multiblock by registerMultiblock(ECRModIDs.MAGICAL_TELEPORTER) { MagicalTeleporter }

    private val configuredMultiblocks = createConfiguredRegistries(
        ECRegistries.MULTIBLOCK,
        ECConfig.current.multiblocks.customMultiblockRegistryIds(),
        multiblocks.entries.mapTo(linkedSetOf()) { holder -> holder.id },
        "custom_ids",
        "multiblock"
    ) { Multiblock.jsonOnly() }
    private val configuredAssembledMultiblocks = createConfiguredRegistries(
        ECRegistries.ASSEMBLED_MULTIBLOCK,
        ECConfig.current.multiblocks.customAssembledRegistryIds(),
        assembled.entries.mapTo(linkedSetOf()) { holder -> holder.id },
        "custom_assembled_ids",
        "assembled multiblock",
        AssembledMultiblockDefinition::jsonOnly
    )

    init {
        multiblocks.register(bus)
        assembled.register(bus)
        configuredMultiblocks.forEach { registry -> registry.register(bus) }
        configuredAssembledMultiblocks.forEach { registry -> registry.register(bus) }
    }

    private fun registerMultiblock(id: String, fallback: () -> Multiblock): ReadOnlyProperty<Any?, Multiblock> {
        val registered = multiblocks.register(id) { _ -> fallback() }
        return ReadOnlyProperty { _, _ -> MultiblockDefinitions[id.ecRL] ?: registered.get() }
    }

    private fun registerAssembled(
        id: String,
        fallback: () -> AssembledMultiblockDefinition
    ): ReadOnlyProperty<Any?, AssembledMultiblockDefinition> {
        val registered = assembled.register(id) { _ -> fallback() }
        return ReadOnlyProperty { _, _ -> MultiblockDefinitions.assembled(id.ecRL) ?: registered.get() }
    }

    private fun <T : Any> createConfiguredRegistries(
        registry: Registry<T>,
        ids: Set<Identifier>,
        occupiedIds: Set<Identifier>,
        configKey: String,
        kind: String,
        factory: (Identifier) -> T
    ): List<DeferredRegister<T>> {
        ids.forEach { id ->
            check(id !in occupiedIds) {
                "Configured custom $kind $id is already registered; remove it from $configKey " +
                    "and use its JSON file as an override"
            }
        }

        return ids.groupBy(Identifier::getNamespace).map { (namespace, namespaceIds) ->
            DeferredRegister.create(registry, namespace).also { deferred ->
                namespaceIds.forEach { id ->
                    deferred.register(id.path) { registeredId -> factory(registeredId) }
                }
            }
        }
    }
}
