package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.common.item.BoundGemItem
import com.algorithmlx.ecr.common.item.Hammer
import com.algorithmlx.ecr.common.item.ResearchBookItem
import com.algorithmlx.ecr.common.item.SoulStone
import com.algorithmlx.ecr.common.item.tool.*
import net.minecraft.world.item.Item

interface ItemRegistry {
    val hammer: Hammer
    val soulStone: SoulStone
    val researchBook: ResearchBookItem
    val boundGem: BoundGemItem

    val weaknessElementalAxe: WeakAxe
    val weaknessElementalHoe: WeakHoe
    val weaknessElementalPickaxe: WeakPickaxe
    val weaknessElementalShovel: WeakShovel
    val weaknessElementalSword: WeakSword

    val elementalGem: Item
    val flameGem: Item
    val waterGem: Item
    val earthGem: Item
    val airGem: Item

    val elementalCore: Item
    val combinedMagicAlloys: Item
    val demonicCore: Item
    val diamondPlate: Item
    val emeraldPlate: Item
    val enderScaleAlloy: Item
    val forcefieldCore: Item
    val forcefieldPlating: Item
    val fortifiedFrame: Item
    val fortifiedPlate: Item
    val magicPlate: Item
    val magicPurifiedBlazeAlloy: Item
    val magicPurifiedEnderScaleAlloy: Item
    val magicPurifiedGlassAlloy: Item
    val obsidianPlate: Item
    val paleCore: Item
    val palePlate: Item
    val particleCatcher: Item
    val particleEmitter: Item
    val sunImbuedGlass: Item
    val voidPlating: Item
    val mithrilineIngot: Item
    val magicalIngot: Item
    val magicalSlag: Item
    val mithrilineDust: Item
    val heatingRod: Item
    val mithrilineCrystalGem: Item
    val mruResonatingCrystal: Item
    val fadingCrystal: Item
    val eyeOfAbsorption: Item
    val heatCore: Item
    val monocle: Item

    companion object {
        @JvmStatic
        lateinit var instance: ItemRegistry
    }
}
