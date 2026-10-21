package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.common.block.entity.AssembledMultiblockPartBlockEntity
import com.algorithmlx.ecr.common.block.entity.ColdDistillerEntity
import com.algorithmlx.ecr.common.block.entity.CreativeMRUSourceEntity
import com.algorithmlx.ecr.common.block.entity.HeatGeneratorEntity
import com.algorithmlx.ecr.common.block.entity.MagicTableBlockEntity
import com.algorithmlx.ecr.common.block.entity.MagicalTeleporterEntity
import com.algorithmlx.ecr.common.block.entity.MatrixDestructorEntity
import com.algorithmlx.ecr.common.block.entity.MithrilineFurnaceEntity
import com.algorithmlx.ecr.common.block.entity.RadiatingChamberEntity
import com.algorithmlx.ecr.common.block.entity.RayTowerEntity
import com.algorithmlx.ecr.common.block.entity.SunRayAbsorberEntity
import com.algorithmlx.ecr.common.block.entity.enrichment.EnrichmentChamberControllerEntity
import com.algorithmlx.ecr.common.block.entity.enrichment.EnrichmentChamberExtractorEntity
import com.algorithmlx.ecr.common.block.entity.enrichment.EnrichmentChamberReceiverEntity
import com.algorithmlx.ecr.common.inventory.*
import com.algorithmlx.ecr.init.ECRModIDs
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import java.util.function.Supplier

object BlockEntityTypeRegistry : RegistrationHandler(ModId) {
    val mimCore = register(ECRModIDs.MIM_CORE) { BlockEntityType(::MIMCoreEntity, setOf(BlockRegistry.mimCore.get())) }
    val mimStorage = register(ECRModIDs.MIM_STORAGE) { BlockEntityType(::MIMStorageEntity, setOf(BlockRegistry.mimStorage.get())) }
    val mimStorageNode = register(ECRModIDs.MIM_STORAGE_NODE) {
        BlockEntityType(::MIMStorageNodeEntity, setOf(BlockRegistry.mimStorageNode.get()))
    }
    val mimScreen = register(ECRModIDs.MIM_SCREEN) { BlockEntityType(::MIMScreenEntity, setOf(BlockRegistry.mimScreen.get())) }
    val mimCrafting = register(ECRModIDs.MIM_CRAFTING) { BlockEntityType(::MIMCraftingEntity, setOf(BlockRegistry.mimCrafting.get())) }
    val mimImport: Supplier<BlockEntityType<MIMNodeEntity>> = register(ECRModIDs.MIM_IMPORT) { BlockEntityType({ pos, state ->
        MIMNodeEntity(pos, state, mimImport.get(), imports = true, persistent = false)
    }, setOf(BlockRegistry.mimImport.get())) }
    val mimExport: Supplier<BlockEntityType<MIMNodeEntity>> = register(ECRModIDs.MIM_EXPORT) { BlockEntityType({ pos, state ->
        MIMNodeEntity(pos, state, mimExport.get(), imports = false, persistent = false)
    }, setOf(BlockRegistry.mimExport.get())) }
    val mimPersistentImport: Supplier<BlockEntityType<MIMNodeEntity>> = register(ECRModIDs.MIM_PERSISTENT_IMPORT) { BlockEntityType({ pos, state ->
        MIMNodeEntity(pos, state, mimPersistentImport.get(), imports = true, persistent = true)
    }, setOf(BlockRegistry.mimPersistentImport.get())) }
    val mimPersistentExport: Supplier<BlockEntityType<MIMNodeEntity>> = register(ECRModIDs.MIM_PERSISTENT_EXPORT) { BlockEntityType({ pos, state ->
        MIMNodeEntity(pos, state, mimPersistentExport.get(), imports = false, persistent = true)
    }, setOf(BlockRegistry.mimPersistentExport.get())) }
    val magicalMirror = register(ECRModIDs.MAGICAL_MIRROR) { BlockEntityType(::MagicalMirrorEntity, setOf(BlockRegistry.magicalMirror.get())) }

    val assembledMultiblockPart = register(ECRModIDs.ASSEMBLED_MULTIBLOCK_PART) {
        BlockEntityType(::AssembledMultiblockPartBlockEntity, setOf(BlockRegistry.assembledMultiblockPart.get()))
    }
    val mithrilineFurnace = register(ECRModIDs.MITHRILINE_FURNACE) {
        BlockEntityType(::MithrilineFurnaceEntity, setOf(BlockRegistry.mithrilineFurnace.get()))
    }
    val radiatingChamber = register(ECRModIDs.RADIATING_CHAMBER) {
        BlockEntityType(::RadiatingChamberEntity, setOf(BlockRegistry.radiatingChamber.get()))
    }
    val magicTable = register(ECRModIDs.MAGIC_TABLE) {
        BlockEntityType(::MagicTableBlockEntity, setOf(BlockRegistry.magicTable.get()))
    }
    val magicalTeleporter = register(ECRModIDs.MAGICAL_TELEPORTER) {
        BlockEntityType(::MagicalTeleporterEntity, setOf(BlockRegistry.magicalTeleporter.get()))
    }
    val enrichmentChamberController = register(ECRModIDs.ENRICHMENT_CHAMBER_CONTROLLER) {
        BlockEntityType(
            ::EnrichmentChamberControllerEntity,
            setOf(BlockRegistry.enrichmentChamberController.get())
        )
    }
    val enrichmentChamberExtractor = register(ECRModIDs.ENRICHMENT_CHAMBER_EXTRACTOR) {
        BlockEntityType(
            ::EnrichmentChamberExtractorEntity,
            setOf(BlockRegistry.enrichmentChamberExtractor.get())
        )
    }
    val enrichmentChamberReceiver = register(ECRModIDs.ENRICHMENT_CHAMBER_RECEIVER) {
        BlockEntityType(
            ::EnrichmentChamberReceiverEntity,
            setOf(BlockRegistry.enrichmentChamberReceiver.get())
        )
    }
    val rayTower = register(ECRModIDs.RAY_TOWER) {
        BlockEntityType(::RayTowerEntity, setOf(BlockRegistry.rayTower.get()))
    }
    val creativeMRUSource = register(ECRModIDs.CREATIVE_MRU_SOURCE) {
        BlockEntityType(::CreativeMRUSourceEntity, setOf(BlockRegistry.creativeMRUSource.get()))
    }
    val matrixDestructor = register(ECRModIDs.MATRIX_DESTRUCTOR) {
        BlockEntityType(::MatrixDestructorEntity, setOf(BlockRegistry.matrixDestructor.get()))
    }
    val coldDistiller = register(ECRModIDs.COLD_DISTILLER) {
        BlockEntityType(::ColdDistillerEntity, setOf(BlockRegistry.coldDistiller.get()))
    }
    val sunAbsorber = register(ECRModIDs.SUN_ABSORBER) {
        BlockEntityType(::SunRayAbsorberEntity, setOf(BlockRegistry.sunAbsorber.get()))
    }
    val heatGenerator = register(ECRModIDs.HEAT_GENERATOR) {
        BlockEntityType(::HeatGeneratorEntity, setOf(BlockRegistry.heatGenerator.get()))
    }

    private fun <T : BlockEntity> register(id: String, factory: () -> BlockEntityType<T>) =
        registerNoEntry(id, BuiltInRegistries.BLOCK_ENTITY_TYPE, factory)
}
