package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.common.item.BoundGemItem
import com.algorithmlx.ecr.common.item.Hammer
import com.algorithmlx.ecr.common.item.ResearchBookItem
import com.algorithmlx.ecr.common.item.SoulStone
import com.algorithmlx.ecr.common.item.tool.*
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import java.util.function.Supplier

object ItemRegistry: RegistrationHandler(ModId) {
    val hammer = register(ECRModIDs.HAMMER, ::Hammer)
    val soulStone = register(ECRModIDs.SOUL_STONE, ::SoulStone)
    val researchBook = register(ECRModIDs.RESEARCH_BOOK, ::ResearchBookItem) {
        Item.Properties().component(
            DataComponentRegistry.bookType.get(),
            BookTypeRegistry.basic.key,
        )
    }

    val boundGem = register(ECRModIDs.BOUND_GEM, ::BoundGemItem)

    val weaknessElementalAxe = register(ECRModIDs.WEAKNESS_ELEMENTAL_AXE, ::WeakAxe)
    val weaknessElementalHoe = register(ECRModIDs.WEAKNESS_ELEMENTAL_HOE, ::WeakHoe)
    val weaknessElementalPickaxe = register(ECRModIDs.WEAKNESS_ELEMENTAL_PICKAXE, ::WeakPickaxe)
    val weaknessElementalShovel = register(ECRModIDs.WEAKNESS_ELEMENTAL_SHOVEL, ::WeakShovel)
    val weaknessElementalSword = register(ECRModIDs.WEAKNESS_ELEMENTAL_SWORD, ::WeakSword)

    val elementalGem = basicItem(ECRModIDs.ELEMENTAL_GEM)
    val flameGem = basicItem(ECRModIDs.FLAME_GEM)
    val waterGem = basicItem(ECRModIDs.WATER_GEM)
    val earthGem = basicItem(ECRModIDs.EARTH_GEM)
    val airGem = basicItem(ECRModIDs.AIR_GEM)

    val elementalCore = basicItem(ECRModIDs.ELEMENTAL_CORE)
    val combinedMagicAlloys = basicItem(ECRModIDs.COMBINED_MAGIC_ALLOYS)
    val demonicCore = basicItem(ECRModIDs.DEMONIC_CORE)
    val diamondPlate = basicItem(ECRModIDs.DIAMOND_PLATE)
    val emeraldPlate = basicItem(ECRModIDs.EMERALD_PLATE)
    val enderScaleAlloy = basicItem(ECRModIDs.ENDER_SCALE_ALLOY)
    val forcefieldCore = basicItem(ECRModIDs.FORCEFIELD_CORE)
    val forcefieldPlating = basicItem(ECRModIDs.FORCIFIELD_PLATING)
    val fortifiedFrame = basicItem(ECRModIDs.FORTIFIED_FRAME)
    val fortifiedPlate = basicItem(ECRModIDs.FORTIFIED_PLATE)
    val magicPlate = basicItem(ECRModIDs.MAGIC_PLATE)
    val magicPurifiedBlazeAlloy = basicItem(ECRModIDs.MAGIC_PURIFIED_BLAZE_ALLOY)
    val magicPurifiedEnderScaleAlloy = basicItem(ECRModIDs.MAGIC_PURIFIED_ENDER_SCALE_ALLOY)
    val magicPurifiedGlassAlloy = basicItem(ECRModIDs.MAGIC_PURIFIED_GLASS_ALLOY)
    val obsidianPlate = basicItem(ECRModIDs.OBSIDIAN_PLATE)
    val paleCore = basicItem(ECRModIDs.PALE_CORE)
    val palePlate = basicItem(ECRModIDs.PALE_PLATE)
    val particleCatcher = basicItem(ECRModIDs.PARTICLE_CATCHER)
    val particleEmitter = basicItem(ECRModIDs.PARTICLE_EMITTER)
    val sunImbuedGlass = basicItem(ECRModIDs.SUN_IMBUED_GLASS)
    val voidPlating = basicItem(ECRModIDs.VOID_PLATING)
    val mithrilineIngot = basicItem(ECRModIDs.MITHRILINE_INGOT)
    val magicalIngot = basicItem(ECRModIDs.MAGICAL_INGOT)
    val magicalSlag = basicItem(ECRModIDs.MAGICAL_SLAG)
    val mithrilineDust = basicItem(ECRModIDs.MITHRILINE_DUST)
    val heatingRod = basicItem(ECRModIDs.HEATING_ROD)
    val mithrilineCrystalGem = basicItem(ECRModIDs.MITHRILINE_CRYSTAL_GEM)
    val mruResonatingCrystal = basicItem(ECRModIDs.MRU_RESONATING_CRYSTAL)
    val fadingCrystal = basicItem(ECRModIDs.FADING_CRYSTAL)
    val eyeOfAbsorption = basicItem(ECRModIDs.EYE_OF_ABSORPTION)
    val heatCore = basicItem(ECRModIDs.HEAT_CORE)
    val monocle = basicItem(ECRModIDs.MONOCLE)

    private fun basicItem(id: String, properties: () -> Item.Properties = { Item.Properties() }) =
        register(id, ::Item, properties)

    private fun <T: Item> register(
        id: String,
        item: (Item.Properties) -> T,
        properties: () -> Item.Properties = { Item.Properties() }
    ): Supplier<T> {
        val itemKey = { it: Identifier -> ResourceKey.create(Registries.ITEM, it) }
        val entryId = id.ecRL
        return registerItem(id) {
            item(properties().setId(itemKey(entryId)))
        }
    }
}
