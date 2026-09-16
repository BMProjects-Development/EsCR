package com.algorithmlx.ecr.fabric.init.registry

import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.common.item.*
import com.algorithmlx.ecr.common.item.tool.*
import com.algorithmlx.ecr.registry.BookTypeRegistry
import com.algorithmlx.ecr.registry.DataComponentRegistry
import com.algorithmlx.ecr.registry.ItemRegistry
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item

object FabricItemRegistry: ItemRegistry {
    override val hammer: Hammer = register(ECRModIDs.HAMMER, ::Hammer)
    override val soulStone: SoulStone = register(ECRModIDs.SOUL_STONE, ::SoulStone)
    override val researchBook: ResearchBookItem = register(
        ECRModIDs.RESEARCH_BOOK, ::ResearchBookItem, Item.Properties().component(
            DataComponentRegistry.instance.bookType,
            ECRegistries.BOOK_TYPES.getResourceKey(BookTypeRegistry.instance.basic).get()
        )
    )
    override val boundGem: BoundGemItem = register(ECRModIDs.BOUND_GEM, ::BoundGemItem)

    override val weaknessElementalAxe: WeakAxe = register(ECRModIDs.WEAKNESS_ELEMENTAL_AXE, ::WeakAxe)
    override val weaknessElementalHoe: WeakHoe = register(ECRModIDs.WEAKNESS_ELEMENTAL_HOE, ::WeakHoe)
    override val weaknessElementalPickaxe: WeakPickaxe = register(ECRModIDs.WEAKNESS_ELEMENTAL_PICKAXE, ::WeakPickaxe)
    override val weaknessElementalShovel: WeakShovel = register(ECRModIDs.WEAKNESS_ELEMENTAL_SHOVEL, ::WeakShovel)
    override val weaknessElementalSword: WeakSword = register(ECRModIDs.WEAKNESS_ELEMENTAL_SWORD, ::WeakSword)

    override val elementalGem: Item = basicItem(ECRModIDs.ELEMENTAL_GEM)
    override val flameGem: Item = basicItem(ECRModIDs.FLAME_GEM)
    override val waterGem: Item = basicItem(ECRModIDs.WATER_GEM)
    override val earthGem: Item = basicItem(ECRModIDs.EARTH_GEM)
    override val airGem: Item = basicItem(ECRModIDs.AIR_GEM)

    override val elementalCore: Item = basicItem(ECRModIDs.ELEMENTAL_CORE)
    override val combinedMagicAlloys: Item = basicItem(ECRModIDs.COMBINED_MAGIC_ALLOYS)
    override val demonicCore: Item = basicItem(ECRModIDs.DEMONIC_CORE)
    override val diamondPlate: Item = basicItem(ECRModIDs.DIAMOND_PLATE)
    override val emeraldPlate: Item = basicItem(ECRModIDs.EMERALD_PLATE)
    override val enderScaleAlloy: Item = basicItem(ECRModIDs.ENDER_SCALE_ALLOY)
    override val forcefieldCore: Item = basicItem(ECRModIDs.FORCEFIELD_CORE)
    override val forcefieldPlating: Item = basicItem(ECRModIDs.FORCIFIELD_PLATING)
    override val fortifiedFrame: Item = basicItem(ECRModIDs.FORTIFIED_FRAME)
    override val fortifiedPlate: Item = basicItem(ECRModIDs.FORTIFIED_PLATE)
    override val magicPlate: Item = basicItem(ECRModIDs.MAGIC_PLATE)
    override val magicPurifiedBlazeAlloy: Item = basicItem(ECRModIDs.MAGIC_PURIFIED_BLAZE_ALLOY)
    override val magicPurifiedEnderScaleAlloy: Item = basicItem(ECRModIDs.MAGIC_PURIFIED_ENDER_SCALE_ALLOY)
    override val magicPurifiedGlassAlloy: Item = basicItem(ECRModIDs.MAGIC_PURIFIED_GLASS_ALLOY)
    override val obsidianPlate: Item = basicItem(ECRModIDs.OBSIDIAN_PLATE)
    override val paleCore: Item = basicItem(ECRModIDs.PALE_CORE)
    override val palePlate: Item = basicItem(ECRModIDs.PALE_PLATE)
    override val particleCatcher: Item = basicItem(ECRModIDs.PARTICLE_CATCHER)
    override val particleEmitter: Item = basicItem(ECRModIDs.PARTICLE_EMITTER)
    override val sunImbuedGlass: Item = basicItem(ECRModIDs.SUN_IMBUED_GLASS)
    override val voidPlating: Item = basicItem(ECRModIDs.VOID_PLATING)
    override val mithrilineIngot: Item = basicItem(ECRModIDs.MITHRILINE_INGOT)
    override val magicalIngot: Item = basicItem(ECRModIDs.MAGICAL_INGOT)
    override val magicalSlag: Item = basicItem(ECRModIDs.MAGICAL_SLAG)
    override val mithrilineDust: Item = basicItem(ECRModIDs.MITHRILINE_DUST)
    override val heatingRod: Item = basicItem(ECRModIDs.HEATING_ROD)
    override val mithrilineCrystalGem: Item = basicItem(ECRModIDs.MITHRILINE_CRYSTAL_GEM)
    override val mruResonatingCrystal: Item = basicItem(ECRModIDs.MRU_RESONATING_CRYSTAL)
    override val fadingCrystal: Item = basicItem(ECRModIDs.FADING_CRYSTAL)
    override val eyeOfAbsorption: Item = basicItem(ECRModIDs.EYE_OF_ABSORPTION)
    override val heatCore: Item = basicItem(ECRModIDs.HEAT_CORE)
    override val monocle: Item = basicItem(ECRModIDs.MONOCLE)

    private fun basicItem(id: String, properties: Item.Properties = Item.Properties()) = register(id, ::Item, properties)

    private fun <T: Item> register(
        id: String,
        item: (Item.Properties) -> T,
        properties: Item.Properties = Item.Properties()
    ): T {
        val itemKey = { it: Identifier -> ResourceKey.create(Registries.ITEM, it) }
        val resId = id.ecRL
        return Registry.register(BuiltInRegistries.ITEM, id.ecRL, item(properties.setId(itemKey(resId))))
    }
}
