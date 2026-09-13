package com.algorithmlx.ecr.neoforge.init.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.block.JSONBlockProperties
import com.algorithmlx.ecr.api.block.json
import com.algorithmlx.ecr.common.block.*
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.common.item.NamedBlockItem
import com.algorithmlx.ecr.registry.BlockRegistry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.util.valueproviders.UniformInt
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.DropExperienceBlock
import net.minecraft.world.level.block.TransparentBlock
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.material.PushReaction
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredBlock
import net.neoforged.neoforge.registries.DeferredRegister

class NeoForgeBlockRegistry(
    bus: IEventBus,
) : BlockRegistry {
    private val blockItems = DeferredRegister.createItems(ModId)
    private val blocks = DeferredRegister.createBlocks(ModId)

    init {
        blocks.register(bus)
        blockItems.register(bus)
    }

    override val assembledMultiblockPart: AssembledMultiblockPartBlock by register(
        ECRModIDs.ASSEMBLED_MULTIBLOCK_PART,
        ::AssembledMultiblockPartBlock,
        BlockBehaviour.Properties.of()
            .pushReaction(PushReaction.BLOCK)
            .json(),
        shouldRegisterItem = false
    )
    override val mithrilineFurnace: MithrilineFurnace by register(
        ECRModIDs.MITHRILINE_FURNACE, ::MithrilineFurnace
    )
    override val radiatingChamber: RadiatingChamber by register(
        ECRModIDs.RADIATING_CHAMBER, ::RadiatingChamber
    )
    override val mithrilineCrystal: CrystalBlock by register(
        ECRModIDs.MITHRILINE_CRYSTAL, ::CrystalBlock
    )
    override val magicTable: MagicTable by register(
        ECRModIDs.MAGIC_TABLE, ::MagicTable
    )
    override val magicalTeleporter: MagicalTeleporter by register(
        ECRModIDs.MAGICAL_TELEPORTER, ::MagicalTeleporter
    )
    override val matrixDestructor: MatrixDestructor by register(
        ECRModIDs.MATRIX_DESTRUCTOR, ::MatrixDestructor
    )
    override val solarPrism: SolarPrism by register(
        ECRModIDs.SOLAR_PRISM, ::SolarPrism
    )
    override val coldDistiller: ColdDistiller by register(
        ECRModIDs.COLD_DISTILLER, ::ColdDistiller
    )
    override val heatGenerator: HeatGenerator by register(
        ECRModIDs.HEAT_GENERATOR, ::HeatGenerator
    )
    override val voidStone: Block by register(ECRModIDs.VOID_STONE)
    override val mithrilinePlating: Block by register(ECRModIDs.MITHRILINE_PLATING)
    override val pale: Block by register(ECRModIDs.PALE_BLOCK)
    override val palePlating: Block by register(ECRModIDs.PALE_PLATING)
    override val magicPlating: Block by register(ECRModIDs.MAGIC_PLATING)
    override val demonicPlating: Block by register(ECRModIDs.DEMONIC_PLATING)
    override val fortifiedStone: Block by register(ECRModIDs.FORTIFIED_STONE)
    override val flameCluster: ClusterBlock by register(
        ECRModIDs.FLAME_CLUSTER, ::ClusterBlock,
        shouldRegisterItem = false
    )
    override val waterCluster: ClusterBlock by register(
        ECRModIDs.WATER_CLUSTER, ::ClusterBlock,
        shouldRegisterItem = false
    )
    override val earthCluster: ClusterBlock by register(
        ECRModIDs.EARTH_CLUSTER, ::ClusterBlock,
        shouldRegisterItem = false
    )
    override val airCluster: ClusterBlock by register(
        ECRModIDs.AIR_CLUSTER, ::ClusterBlock,
        shouldRegisterItem = false
    )
    override val fortifiedGlass: Block by register(
        ECRModIDs.FORTIFIED_GLASS, ::TransparentBlock
    )
    override val enrichmentChamberHolder: Block by register(ECRModIDs.ENRICHMENT_CHAMBER_HOLDER)
    override val enrichmentChamberController: EnrichmentChamberController by register(
        ECRModIDs.ENRICHMENT_CHAMBER_CONTROLLER, ::EnrichmentChamberController
    )
    override val enrichmentChamberExtractor: EnrichmentChamberExtractor by register(
        ECRModIDs.ENRICHMENT_CHAMBER_EXTRACTOR, ::EnrichmentChamberExtractor
    )
    override val enrichmentChamberReceiver: EnrichmentChamberReceiver by register(
        ECRModIDs.ENRICHMENT_CHAMBER_RECEIVER, ::EnrichmentChamberReceiver
    )
    override val rayTowerBase: RayTowerBase by register(
        ECRModIDs.RAY_TOWER_BASE, ::RayTowerBase
    )
    override val rayTower: RayTower by register(
        ECRModIDs.RAY_TOWER, ::RayTower
    )
    override val creativeMRUSource: CreativeMRUSource by register(
        ECRModIDs.CREATIVE_MRU_SOURCE, ::CreativeMRUSource
    )
    override val mithrilineOre: DropExperienceBlock by register(ECRModIDs.MITHRILINE_ORE, {
        DropExperienceBlock(UniformInt.of(0, 5), it)
    })
    override val deepslateMithrilineOre: DropExperienceBlock by register(ECRModIDs.DEEPSLATE_MITHRILINE_ORE, {
        DropExperienceBlock(UniformInt.of(0, 5), it)
    })

    private fun register(
        id: String,
        properties: JSONBlockProperties = BlockBehaviour.Properties.of().json(),
        shouldRegisterItem: Boolean = true
    ) = register(id, ::Block, properties, shouldRegisterItem)

    private fun <B: Block> register(
        id: String,
        block: (BlockBehaviour.Properties) -> B,
        properties: JSONBlockProperties = BlockBehaviour.Properties.of().json(),
        shouldRegisterItem: Boolean = true
    ): DeferredBlock<B> {
        val blockKey = { it: Identifier -> ResourceKey.create(Registries.BLOCK, it) }
        val registered = blocks.register(id) { rk ->
            block(properties.load(rk).setId(blockKey(rk)))
        }

        if (shouldRegisterItem) {
            blockItems.register(id) { rk -> NamedBlockItem(registered.get(), Item.Properties().setId(ResourceKey.create(Registries.ITEM, rk)).useBlockDescriptionPrefix()) }
        }

        return registered
    }
}
