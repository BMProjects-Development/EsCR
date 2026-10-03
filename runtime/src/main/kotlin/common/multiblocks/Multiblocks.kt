package com.algorithmlx.ecr.common.multiblocks

import com.algorithmlx.ecr.api.geo.GeoBlockRotation
import com.algorithmlx.ecr.api.geo.GeoLightMode
import com.algorithmlx.ecr.api.multiblock.Multiblock
import com.algorithmlx.ecr.api.multiblock.MultiblockMatcher
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledBlockMatcher
import com.algorithmlx.ecr.api.multiblock.assembled.assembledMultiblock
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.init.ECRTags
import com.algorithmlx.ecr.registry.BlockRegistry
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

val RayTowerMultiblock =
    assembledMultiblock(
        "ray_tower".ecRL,
        allowAssemblyFromAnyPart = true
    ) {
        fun shape(): VoxelShape {
            var shape = Shapes.empty()

            shape = Shapes.join(shape, Shapes.box(0.0, 0.0625, 0.0, 0.25, 1.1875, 0.25), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.75, 0.0625, 0.0, 1.0, 1.1875, 0.25), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.75, 0.0625, 0.75, 1.0, 1.1875, 1.0), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.0, 0.0625, 0.75, 0.25, 1.1875, 1.0), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.25, 0.0625, 0.25, 0.75, 1.375, 0.75), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.25, 0.0, 0.125, 0.75, 0.125, 0.25), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.25, 0.0, 0.75, 0.75, 0.125, 0.875), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.125, 0.0, 0.25, 0.25, 0.125, 0.75), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.75, 0.0, 0.25, 0.875, 0.125, 0.75), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(-0.0625, 0.0, -0.0625, 0.3125, 0.0625, 0.3125), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.6875, 0.0, -0.0625, 1.0625, 0.0625, 0.3125), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.6875, 0.0, 0.6875, 1.0625, 0.0625, 1.0625), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(-0.0625, 0.0, 0.6875, 0.3125, 0.0625, 1.0625), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.125, 1.1875, 0.125, 0.875, 1.3125, 0.875), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.06244, 1.18744, 0.06244, 0.2500625, 1.8750625, 0.2500625), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.06244, 1.18744, 0.74994, 0.2500625, 1.8750625, 0.9375625), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.74994, 1.18744, 0.74994, 0.9375625, 1.8750625, 0.9375625), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.74994, 1.18744, 0.06244, 0.9375625, 1.8750625, 0.2500625), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.1875, 1.8125, 0.1875, 0.8125, 1.9375, 0.3125), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.1875, 1.8125, 0.6875, 0.8125, 1.9375, 0.8125), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.1875, 1.8125, 0.3125, 0.3125, 1.9375, 0.6875), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.6875, 1.8125, 0.3125, 0.8125, 1.9375, 0.6875), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.25, 1.9375, 0.25, 0.75, 2.0, 0.3125), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.25, 1.9375, 0.6875, 0.75, 2.0, 0.75), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.25, 1.9375, 0.3125, 0.3125, 2.0, 0.6875), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.6875, 1.9375, 0.3125, 0.75, 2.0, 0.6875), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.25, 1.75, 0.25, 0.75, 1.8125, 0.3125), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.25, 1.75, 0.3125, 0.3125, 1.8125, 0.6875), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.6875, 1.75, 0.3125, 0.75, 1.8125, 0.6875), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.25, 1.75, 0.6875, 0.75, 1.8125, 0.75), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.6875, 1.75, 0.6875, 1.0, 1.8125, 1.0), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.0, 1.75, 0.6875, 0.3125, 1.8125, 1.0), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.0, 1.75, 0.0, 0.3125, 1.8125, 0.3125), BooleanOp.OR)
            shape = Shapes.join(shape, Shapes.box(0.6875, 1.75, 0.0, 1.0, 1.8125, 0.3125), BooleanOp.OR)

            return shape
        }

        controller(AssembledBlockMatcher.block(BlockRegistry.rayTower.get()))
        part(BlockPos(0, -1, 0), AssembledBlockMatcher.block(BlockRegistry.rayTowerBase.get()))
        formedModelAnchor(0, -1, 0)
        formedShape(shape())
        formedModel(
            ECRModIDs.RAY_TOWER.ecRL,
            ECRModIDs.textureLocation("block/assembled/${ECRModIDs.RAY_TOWER}"),
            lightMode = GeoLightMode.WORLD,
            blockRotation = GeoBlockRotation.NONE
        )
    }

object MithrilineFurnaceMultiblock : Multiblock(5, 5, 3, {
    val a = block(BlockRegistry.mithrilinePlating.get().defaultBlockState())
    val b = block(BlockRegistry.mithrilineFurnace.get().defaultBlockState())

    pattern(
        a, null, a, null, a,
        null, a, a, a, null,
        a, a, null, a, a,
        null, a, a, a, null,
        a, null, a, null, a,

        a, null, a, null, a,
        null, null, null, null, null,
        a, null, b, null, a,
        null, null, null, null, null,
        a, null, a, null, a,

        a, null, null, null, a,
        null, null, null, null, null,
        null, null, null, null, null,
        null, null, null, null, null,
        a, null, null, null, a
    )
})

object SoulStoneMultiblock : Multiblock(3, 3, 1, {
    this.makeRecipeMB(
        this.tag(BlockTags.SOUL_SPEED_BLOCKS),
        this.block(Blocks.EMERALD_BLOCK.defaultBlockState())
    )
})

object FlameCrystal : Multiblock(3, 3, 1, {
    this.makeRecipeMB(
        this.block(Blocks.LAVA.defaultBlockState()),
        this.tag(BlockTags.INFINIBURN_NETHER)
    )
})

object WaterCrystal : Multiblock(3, 3, 1, {
    this.makeRecipeMB(
        this.block(Blocks.WATER.defaultBlockState()),
        this.tag(BlockTags.ICE)
    )
})

object EarthCrystal : Multiblock(3, 3, 1, {
    this.makeRecipeMB(
        this.block(Blocks.MOSSY_COBBLESTONE.defaultBlockState()),
        this.block(Blocks.MOSS_BLOCK.defaultBlockState())
    )
})

object AirCrystal : Multiblock(3, 3, 1, {
    this.makeRecipeMB(
        this.block(Blocks.END_STONE_BRICKS.defaultBlockState()),
        this.block(Blocks.PURPUR_BLOCK.defaultBlockState())
    )
})

object LightningCollector : Multiblock(11, 11, 4, {
    val cutCopperSlabList = Blocks.CUT_COPPER_SLAB.asList().map { this.block(it.defaultBlockState()) }
    val copperBlockList = Blocks.COPPER_BLOCK.asList().map { this.block(it.defaultBlockState()) }
    val lightningRodList = Blocks.LIGHTNING_ROD.asList().map { this.block(it.defaultBlockState()) }

    val voidStone = this.block(BlockRegistry.voidStone.get().defaultBlockState())
    val mithrilinePlating = this.block(BlockRegistry.mithrilinePlating.get().defaultBlockState())
    val copperSlabs = this.list(cutCopperSlabList)
    val copperBlocks = this.list(copperBlockList)
    val center = this.block(BlockRegistry.mithrilineFurnace.get().defaultBlockState())
    val mithrilineCrystal = this.block(BlockRegistry.mithrilineCrystal.get().defaultBlockState())
    val lightningRod = this.list(lightningRodList)

    pattern(
        null, null, null, voidStone, voidStone, null, voidStone, voidStone, null, null, null,
        null, mithrilinePlating, mithrilinePlating, null, null, voidStone, null, null, mithrilinePlating, mithrilinePlating, null,
        null, mithrilinePlating, null, null, voidStone, null, voidStone, null, null, mithrilinePlating, null,
        voidStone, null, null, voidStone, null, voidStone, null, voidStone, null, null, voidStone,
        voidStone, null, voidStone, null, null, null, null, null, voidStone, null, voidStone,
        null, voidStone, null, voidStone, null, null, null, voidStone, null, voidStone, null,
        voidStone, null, voidStone, null, null, null, null, null, voidStone, null, voidStone,
        voidStone, null, null, voidStone, null, voidStone, null, voidStone, null, null, voidStone,
        null, mithrilinePlating, null, null, voidStone, null, voidStone, null, null, mithrilinePlating, null,
        null, mithrilinePlating, mithrilinePlating, null, null, voidStone, null, null, mithrilinePlating, mithrilinePlating, null,
        null, null, null, voidStone, voidStone, null, voidStone, voidStone, null, null, null,

        null, null, null, null, null, null, null, null, null, null, null,
        null, mithrilinePlating, null, null, null, null, null, null, null, mithrilinePlating, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, copperSlabs, null, copperSlabs, null, null, null, null,
        null, null, null, copperSlabs, copperBlocks, voidStone, copperBlocks, copperSlabs, null, null, null,
        null, null, null, null, voidStone, null, voidStone, null, null, null, null,
        null, null, null, copperSlabs, copperBlocks, voidStone, copperBlocks, copperSlabs, null, null, null,
        null, null, null, null, copperSlabs, null, copperSlabs, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, mithrilinePlating, null, null, null, null, null, null, null, mithrilinePlating, null,
        null, null, null, null, null, null, null, null, null, null, null,

        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, center, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,

        null, null, null, null, null, null, null, null, null, null, null,
        null, mithrilineCrystal, null, null, null, null, null, null, null, mithrilineCrystal, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, lightningRod, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, null, null, null, null, null, null, null, null, null, null,
        null, mithrilineCrystal, null, null, null, null, null, null, null, mithrilineCrystal, null,
        null, null, null, null, null, null, null, null, null, null, null
    )
})

object EnrichmentChamber : Multiblock(128, 128, 128, {
    val outerFrame = this.block(BlockRegistry.magicPlating.get().defaultBlockState())
    val innerFrame = this.list(
        this.tag(ECRTags.Blocks.ENRICHMENT_CHAMBER),
        this.block(
            BlockRegistry.enrichmentChamberController.get().defaultBlockState(),
            ignoreTag = true
        )
    )
    val air = this.block(Blocks.AIR.defaultBlockState())

    this.scalablePattern(2..<64) {
        when {
            isEdge -> outerFrame
            isBoundary -> innerFrame
            else -> air
        }
    }
})

object MagicalTeleporter : Multiblock(5, 5, 3, {
    val a = this.block(BlockRegistry.voidStone.get().defaultBlockState())
    val b = this.block(BlockRegistry.magicPlating.get().defaultBlockState())
    val c = this.block(BlockRegistry.magicalTeleporter.get().defaultBlockState(), ignoreTag = true)

    pattern(
        a, a, b, a, a,
        a, a, a, a, a,
        b, a, c, a, b,
        a, a, a, a, a,
        a, a, b, a, a,

        null, a, null, a, null,
        a, null, null, null, a,
        null, null, null, null, null,
        a, null, null, null, a,
        null, a, null, a, null,

        null, a, null, a, null,
        a, null, null, null, a,
        null, null, null, null, null,
        a, null, null, null, a,
        null, a, null, a, null
    )
})

object SunAbsorber : Multiblock(5, 5, 9, {
    val a = this.block(Blocks.AIR.defaultBlockState())
    val b = this.block(BlockRegistry.fortifiedStone.get().defaultBlockState())
    val c = this.block(BlockRegistry.fortifiedGlass.get().defaultBlockState())
    val d = this.block(BlockRegistry.voidStone.get().defaultBlockState())
    val e = this.block(BlockRegistry.solarPrism.get().defaultBlockState())
    val f = null

    pattern(
        d, d, b, null, b, d, d,
        d, b, b, b, b, b, d,
        b, b, d, d, d, b, b,
        null, b, d, null, d, b, null,
        b, b, d, d, d, b, b,
        d, b, b, b, b, b, d,
        d, d, b, null, b, d, d,

        a, a, a, a, a, a, a, a,
        a, a, a, b, a, a, a,
        a, a, a, a, a, a, a, a,
        a, b, a, f, a, b, a,
        a, a, a, a, a, a, a, a,
        a, a, a, b, a, a, a,
        a, a, a, a, a, a, a, a,

        a, a, a, b, a, a, a,
        a, a, a, a, a, a, a, a,
        a, a, a, a, a, a, a, a,
        b, a, a, a, a, a, b,
        a, a, a, a, a, a, a, a,
        a, a, a, a, a, a, a, a,
        a, a, a, b, a, a, a,

        a, a, a, c, a, a, a,
        a, a, a, a, a, a, a, a,
        a, a, a, a, a, a, a, a,
        c, a, a, a, a, a, c,
        a, a, a, a, a, a, a, a,
        a, a, a, a, a, a, a, a,
        a, a, a, c, a, a, a,

        a, b, c, c, c, b, a,
        b, b, a, a, a, b, b,
        c, a, a, a, a, a, c,
        c, a, a, a, a, a, c,
        c, a, a, a, a, a, c,
        b, b, a, a, a, b, b,
        a, b, c, c, c, b, a,

        a, a, a, c, a, a, a,
        a, a, a, a, a, a, a, a,
        a, a, a, a, a, a, a, a,
        c, a, a, a, a, a, c,
        a, a, a, a, a, a, a, a,
        a, a, a, a, a, a, a, a,
        a, a, a, c, a, a, a,

        a, a, a, b, a, a, a,
        a, a, a, a, a, a, a, a,
        a, a, a, a, a, a, a, a,
        b, a, a, a, a, a, b,
        a, a, a, a, a, a, a, a,
        a, a, a, a, a, a, a, a,
        a, a, a, b, a, a, a,

        a, a, a, a, a, a, a, a,
        a, a, a, b, a, a, a,
        a, a, a, a, a, a, a, a,
        a, b, a, a, a, b, a,
        a, a, a, a, a, a, a, a,
        a, a, a, b, a, a, a,
        a, a, a, a, a, a, a, a,

        b, b, d, null, d, b, b,
        b, e, e, d, e, e, b,
        d, e, e, e, e, e, d,
        null, d, e, e, e, d, null,
        d, e, e, e, e, e, d,
        b, e, e, d, e, e, b,
        b, b, d, null, d, b, b,
    )
})

private fun Multiblock.makeRecipeMB(
    left: MultiblockMatcher,
    center: MultiblockMatcher
) {
    pattern(
        null,
        left,
        null,
        left,
        center,
        left,
        null,
        left,
        null
    )
}
