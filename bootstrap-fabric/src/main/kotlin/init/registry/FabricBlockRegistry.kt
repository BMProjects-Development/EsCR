package com.algorithmlx.ecr.fabric.init.registry

import com.algorithmlx.ecr.api.block.JSONBlockProperties
import com.algorithmlx.ecr.api.block.json
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.common.block.AssembledMultiblockPartBlock
import com.algorithmlx.ecr.common.block.ClusterBlock
import com.algorithmlx.ecr.common.block.ColdDistiller
import com.algorithmlx.ecr.common.block.CreativeMRUSource
import com.algorithmlx.ecr.common.block.CrystalBlock
import com.algorithmlx.ecr.common.block.EnrichmentChamberController
import com.algorithmlx.ecr.common.block.EnrichmentChamberExtractor
import com.algorithmlx.ecr.common.block.EnrichmentChamberReceiver
import com.algorithmlx.ecr.common.block.HeatGenerator
import com.algorithmlx.ecr.common.block.MagicTable
import com.algorithmlx.ecr.common.block.MagicalTeleporter
import com.algorithmlx.ecr.common.block.MatrixDestructor
import com.algorithmlx.ecr.common.block.MithrilineFurnace
import com.algorithmlx.ecr.common.block.RadiatingChamber
import com.algorithmlx.ecr.common.block.RayTower
import com.algorithmlx.ecr.common.block.RayTowerBase
import com.algorithmlx.ecr.common.block.SolarPrism
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.common.item.NamedBlockItem
import com.algorithmlx.ecr.registry.BlockRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
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

object FabricBlockRegistry : BlockRegistry {
    override val assembledMultiblockPart: AssembledMultiblockPartBlock =
        register(
            ECRModIDs.ASSEMBLED_MULTIBLOCK_PART,
            ::AssembledMultiblockPartBlock,
            BlockBehaviour.Properties
                .of()
                .pushReaction(PushReaction.BLOCK)
                .json(),
            shouldRegisterItem = false,
        )
    override val mithrilineFurnace: MithrilineFurnace = register(
        ECRModIDs.MITHRILINE_FURNACE, ::MithrilineFurnace
    )
    override val radiatingChamber: RadiatingChamber = register(
        ECRModIDs.RADIATING_CHAMBER, ::RadiatingChamber
    )
    override val mithrilineCrystal: CrystalBlock = register(
        ECRModIDs.MITHRILINE_CRYSTAL, ::CrystalBlock
    )
    override val magicTable: MagicTable = register(
        ECRModIDs.MAGIC_TABLE, ::MagicTable
    )
    override val magicalTeleporter: MagicalTeleporter = register(
        ECRModIDs.MAGICAL_TELEPORTER,
        ::MagicalTeleporter
    )
    override val matrixDestructor: MatrixDestructor = register(
        ECRModIDs.MATRIX_DESTRUCTOR, ::MatrixDestructor
    )
    override val solarPrism: SolarPrism = register(
        ECRModIDs.SOLAR_PRISM, ::SolarPrism
    )
    override val coldDistiller: ColdDistiller = register(
        ECRModIDs.COLD_DISTILLER, ::ColdDistiller
    )
    override val heatGenerator: HeatGenerator = register(
        ECRModIDs.HEAT_GENERATOR, ::HeatGenerator
    )
    override val voidStone: Block = registerBasic(ECRModIDs.VOID_STONE)
    override val mithrilinePlating: Block = registerBasic(ECRModIDs.MITHRILINE_PLATING)
    override val pale: Block = registerBasic(ECRModIDs.PALE_BLOCK)
    override val palePlating: Block = registerBasic(ECRModIDs.PALE_PLATING)
    override val magicPlating: Block = registerBasic(ECRModIDs.MAGIC_PLATING)
    override val demonicPlating: Block = registerBasic(ECRModIDs.DEMONIC_PLATING)
    override val fortifiedStone: Block = registerBasic(ECRModIDs.FORTIFIED_STONE)
    override val flameCluster: ClusterBlock = register(
        ECRModIDs.FLAME_CLUSTER, ::ClusterBlock,
        shouldRegisterItem = false
    )
    override val waterCluster: ClusterBlock = register(
        ECRModIDs.WATER_CLUSTER, ::ClusterBlock,
        shouldRegisterItem = false
    )
    override val earthCluster: ClusterBlock = register(
        ECRModIDs.EARTH_CLUSTER, ::ClusterBlock,
        shouldRegisterItem = false
    )
    override val airCluster: ClusterBlock = register(
        ECRModIDs.AIR_CLUSTER, ::ClusterBlock,
        shouldRegisterItem = false
    )
    override val fortifiedGlass: Block = register(
        ECRModIDs.FORTIFIED_GLASS,
        ::TransparentBlock
    )
    override val enrichmentChamberHolder: Block = registerBasic(ECRModIDs.ENRICHMENT_CHAMBER_HOLDER)
    override val enrichmentChamberController: EnrichmentChamberController =
        register(
            ECRModIDs.ENRICHMENT_CHAMBER_CONTROLLER,
            ::EnrichmentChamberController
        )
    override val enrichmentChamberExtractor: EnrichmentChamberExtractor = register(
        ECRModIDs.ENRICHMENT_CHAMBER_EXTRACTOR,
        ::EnrichmentChamberExtractor
    )
    override val enrichmentChamberReceiver: EnrichmentChamberReceiver = register(
        ECRModIDs.ENRICHMENT_CHAMBER_RECEIVER,
        ::EnrichmentChamberReceiver
    )
    override val rayTowerBase: RayTowerBase = register(
        ECRModIDs.RAY_TOWER_BASE, ::RayTowerBase
    )
    override val rayTower: RayTower = register(
        ECRModIDs.RAY_TOWER,
        ::RayTower
    )
    override val creativeMRUSource: CreativeMRUSource = register(
        ECRModIDs.CREATIVE_MRU_SOURCE, ::CreativeMRUSource
    )
    override val mithrilineOre: DropExperienceBlock = register(ECRModIDs.MITHRILINE_ORE, {
        DropExperienceBlock(UniformInt.of(0, 5), it)
    })
    override val deepslateMithrilineOre: DropExperienceBlock = register(ECRModIDs.DEEPSLATE_MITHRILINE_ORE, {
        DropExperienceBlock(UniformInt.of(0, 5), it)
    })

    private fun registerBasic(
        id: String,
        properties: JSONBlockProperties = BlockBehaviour.Properties.of().json(),
        shouldRegisterItem: Boolean = true,
    ) = register(id, ::Block, properties, shouldRegisterItem)

    private fun <B : Block> register(
        id: String,
        block: (BlockBehaviour.Properties) -> B,
        properties: JSONBlockProperties = BlockBehaviour.Properties.of().json(),
        shouldRegisterItem: Boolean = true,
    ): B {
        val blockKey = { it: Identifier -> ResourceKey.create(Registries.BLOCK, it) }
        val regId = id.ecRL
        val configuredProperties = properties.load(regId).setId(blockKey(regId))
        val bl = Registry.register(BuiltInRegistries.BLOCK, regId, block(configuredProperties))

        if (shouldRegisterItem) {
            Registry.register(
                BuiltInRegistries.ITEM,
                regId,
                NamedBlockItem(
                    bl,
                    Item
                        .Properties()
                        .setId(ResourceKey.create(Registries.ITEM, regId))
                        .useBlockDescriptionPrefix(),
                ),
            )
        }

        return bl
    }
}
