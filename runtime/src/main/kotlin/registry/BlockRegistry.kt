package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.block.JSONBlockProperties
import com.algorithmlx.ecr.api.block.json
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.common.block.*
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.common.item.NamedBlockItem
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.util.valueproviders.UniformInt
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DropExperienceBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.PushReaction
import java.util.function.Supplier

object BlockRegistry : RegistrationHandler(ModId) {
    val assembledMultiblockPart = register(
        ECRModIDs.ASSEMBLED_MULTIBLOCK_PART,
        ::AssembledMultiblockPartBlock,
        BlockBehaviour.Properties
            .of()
            .pushReaction(PushReaction.IMMOVEABLE)
            .json(),
        shouldRegisterItem = false
    )
    val mithrilineFurnace = register(ECRModIDs.MITHRILINE_FURNACE, ::MithrilineFurnace)
    val radiatingChamber = register(ECRModIDs.RADIATING_CHAMBER, ::RadiatingChamber)
    val mithrilineCrystal = register(ECRModIDs.MITHRILINE_CRYSTAL, ::CrystalBlock)
    val magicTable = register(ECRModIDs.MAGIC_TABLE, ::MagicTable)
    val magicalTeleporter = register(ECRModIDs.MAGICAL_TELEPORTER, ::MagicalTeleporter)
    val matrixDestructor = register(ECRModIDs.MATRIX_DESTRUCTOR, ::MatrixDestructor)
    val solarPrism = register(ECRModIDs.SOLAR_PRISM, ::SolarPrism)
    val coldDistiller = register(ECRModIDs.COLD_DISTILLER, ::ColdDistiller)
    val heatGenerator = register(ECRModIDs.HEAT_GENERATOR, ::HeatGenerator)
    val voidStone = registerBasic(ECRModIDs.VOID_STONE)
    val mithrilinePlating = registerBasic(ECRModIDs.MITHRILINE_PLATING)
    val pale = registerBasic(ECRModIDs.PALE_BLOCK)
    val palePlating = registerBasic(ECRModIDs.PALE_PLATING)
    val magicPlating = registerBasic(ECRModIDs.MAGIC_PLATING)
    val demonicPlating = registerBasic(ECRModIDs.DEMONIC_PLATING)
    val fortifiedStone = registerBasic(ECRModIDs.FORTIFIED_STONE)
    val flameCluster = register(ECRModIDs.FLAME_CLUSTER, ::ClusterBlock, shouldRegisterItem = false)
    val waterCluster = register(ECRModIDs.WATER_CLUSTER, ::ClusterBlock, shouldRegisterItem = false)
    val earthCluster = register(ECRModIDs.EARTH_CLUSTER, ::ClusterBlock, shouldRegisterItem = false)
    val airCluster = register(ECRModIDs.AIR_CLUSTER, ::ClusterBlock, shouldRegisterItem = false)
    val fortifiedGlass = register(ECRModIDs.FORTIFIED_GLASS, ::OpenTransparentBlock)
    val enrichmentChamberHolder = registerBasic(ECRModIDs.ENRICHMENT_CHAMBER_HOLDER)
    val enrichmentChamberController = register(ECRModIDs.ENRICHMENT_CHAMBER_CONTROLLER, ::EnrichmentChamberController)
    val enrichmentChamberExtractor = register(ECRModIDs.ENRICHMENT_CHAMBER_EXTRACTOR, ::EnrichmentChamberExtractor)
    val enrichmentChamberReceiver = register(ECRModIDs.ENRICHMENT_CHAMBER_RECEIVER, ::EnrichmentChamberReceiver)
    val rayTowerBase = register(ECRModIDs.RAY_TOWER_BASE, ::RayTowerBase)
    val rayTower = register(ECRModIDs.RAY_TOWER, ::RayTower)
    val creativeMRUSource = register(ECRModIDs.CREATIVE_MRU_SOURCE, ::CreativeMRUSource)
    val mithrilineOre = register(ECRModIDs.MITHRILINE_ORE, { DropExperienceBlock(UniformInt.of(0, 5), it) })
    val deepslateMithrilineOre = register(ECRModIDs.DEEPSLATE_MITHRILINE_ORE, { DropExperienceBlock(UniformInt.of(0, 5), it) })

    private fun registerBasic(
        id: String,
        properties: JSONBlockProperties = BlockBehaviour.Properties.of().json(),
        shouldRegisterItem: Boolean = true
    ) = register(id, ::Block, properties, shouldRegisterItem)

    private fun <B: Block> register(
        id: String,
        block: (BlockBehaviour.Properties) -> B,
        properties: JSONBlockProperties = BlockBehaviour.Properties.of().json(),
        shouldRegisterItem: Boolean = true
    ): Supplier<B> = registerDefaulted(id, block, properties.load(id.ecRL), shouldRegisterItem)

    private fun <B: Block> registerDefaulted(
        id: String,
        block: (BlockBehaviour.Properties) -> B,
        properties: BlockBehaviour.Properties = BlockBehaviour.Properties.of(),
        shouldRegisterItem: Boolean = true
    ): Supplier<B> {
        val blockKey = { it: Identifier -> ResourceKey.create(Registries.BLOCK, it) }
        val registryId = id.ecRL
        val entryProperties = properties.setId(blockKey(registryId))

        val entry = registerBlock(id) { block(entryProperties) }

        if (shouldRegisterItem) {
            registerItem(id) {
                NamedBlockItem(
                    entry.get(),
                    Item.Properties()
                        .setId(ResourceKey.create(Registries.ITEM, registryId))
                        .useBlockDescriptionPrefix()
                )
            }
        }

        return entry
    }
}
