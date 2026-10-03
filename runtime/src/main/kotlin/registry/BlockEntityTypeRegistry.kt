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
import com.algorithmlx.ecr.init.ECRModIDs
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType

object BlockEntityTypeRegistry : RegistrationHandler(ModId) {
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
