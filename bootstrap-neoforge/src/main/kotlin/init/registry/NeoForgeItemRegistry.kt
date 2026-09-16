package com.algorithmlx.ecr.neoforge.init.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.api.registries.ECRegistryKeys
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.common.item.BoundGemItem
import com.algorithmlx.ecr.common.item.Hammer
import com.algorithmlx.ecr.common.item.SoulStone
import com.algorithmlx.ecr.common.item.ResearchBookItem
import com.algorithmlx.ecr.common.item.tool.WeakAxe
import com.algorithmlx.ecr.common.item.tool.WeakHoe
import com.algorithmlx.ecr.common.item.tool.WeakPickaxe
import com.algorithmlx.ecr.common.item.tool.WeakShovel
import com.algorithmlx.ecr.common.item.tool.WeakSword
import com.algorithmlx.ecr.registry.DataComponentRegistry
import com.algorithmlx.ecr.registry.ItemRegistry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredItem
import net.neoforged.neoforge.registries.DeferredRegister

class NeoForgeItemRegistry(bus: IEventBus): ItemRegistry {
    private val items = DeferredRegister.createItems(ModId)

    init {
        items.register(bus)
    }

    override val hammer: Hammer by register(ECRModIDs.HAMMER, ::Hammer)
    override val soulStone: SoulStone by register(ECRModIDs.SOUL_STONE, ::SoulStone)
    override val researchBook: ResearchBookItem by register(ECRModIDs.RESEARCH_BOOK, ::ResearchBookItem) {
        Item.Properties().delayedComponent(DataComponentRegistry.instance.bookType) { it.lookupOrThrow(ECRegistryKeys.BOOK_TYPE_KEY).getOrThrow(ResourceKey.create(ECRegistryKeys.BOOK_TYPE_KEY, ECRModIDs.BASIC.ecRL)).key() }
    }
    override val boundGem: BoundGemItem by register(ECRModIDs.BOUND_GEM, ::BoundGemItem)

    override val weaknessElementalAxe: WeakAxe by register(ECRModIDs.WEAKNESS_ELEMENTAL_AXE, ::WeakAxe)
    override val weaknessElementalHoe: WeakHoe by register(ECRModIDs.WEAKNESS_ELEMENTAL_HOE, ::WeakHoe)
    override val weaknessElementalPickaxe: WeakPickaxe by register(ECRModIDs.WEAKNESS_ELEMENTAL_PICKAXE, ::WeakPickaxe)
    override val weaknessElementalShovel: WeakShovel by register(ECRModIDs.WEAKNESS_ELEMENTAL_SHOVEL, ::WeakShovel)
    override val weaknessElementalSword: WeakSword by register(ECRModIDs.WEAKNESS_ELEMENTAL_SWORD, ::WeakSword)

    override val elementalGem: Item by register(ECRModIDs.ELEMENTAL_GEM)
    override val flameGem: Item by register(ECRModIDs.FLAME_GEM)
    override val waterGem: Item by register(ECRModIDs.WATER_GEM)
    override val earthGem: Item by register(ECRModIDs.EARTH_GEM)
    override val airGem: Item by register(ECRModIDs.AIR_GEM)

    override val elementalCore: Item by register(ECRModIDs.ELEMENTAL_CORE)
    override val combinedMagicAlloys: Item by register(ECRModIDs.COMBINED_MAGIC_ALLOYS)
    override val demonicCore: Item by register(ECRModIDs.DEMONIC_CORE)
    override val diamondPlate: Item by register(ECRModIDs.DIAMOND_PLATE)
    override val emeraldPlate: Item by register(ECRModIDs.EMERALD_PLATE)
    override val enderScaleAlloy: Item by register(ECRModIDs.ENDER_SCALE_ALLOY)
    override val forcefieldCore: Item by register(ECRModIDs.FORCEFIELD_CORE)
    override val forcefieldPlating: Item by register(ECRModIDs.FORCIFIELD_PLATING)
    override val fortifiedFrame: Item by register(ECRModIDs.FORTIFIED_FRAME)
    override val fortifiedPlate: Item by register(ECRModIDs.FORTIFIED_PLATE)
    override val magicPlate: Item by register(ECRModIDs.MAGIC_PLATE)
    override val magicPurifiedBlazeAlloy: Item by register(ECRModIDs.MAGIC_PURIFIED_BLAZE_ALLOY)
    override val magicPurifiedEnderScaleAlloy: Item by register(ECRModIDs.MAGIC_PURIFIED_ENDER_SCALE_ALLOY)
    override val magicPurifiedGlassAlloy: Item by register(ECRModIDs.MAGIC_PURIFIED_GLASS_ALLOY)
    override val obsidianPlate: Item by register(ECRModIDs.OBSIDIAN_PLATE)
    override val paleCore: Item by register(ECRModIDs.PALE_CORE)
    override val palePlate: Item by register(ECRModIDs.PALE_PLATE)
    override val particleCatcher: Item by register(ECRModIDs.PARTICLE_CATCHER)
    override val particleEmitter: Item by register(ECRModIDs.PARTICLE_EMITTER)
    override val sunImbuedGlass: Item by register(ECRModIDs.SUN_IMBUED_GLASS)
    override val voidPlating: Item by register(ECRModIDs.VOID_PLATING)
    override val mithrilineIngot: Item by register(ECRModIDs.MITHRILINE_INGOT)
    override val magicalIngot: Item by register(ECRModIDs.MAGICAL_INGOT)
    override val magicalSlag: Item by register(ECRModIDs.MAGICAL_SLAG)
    override val mithrilineDust: Item by register(ECRModIDs.MITHRILINE_DUST)
    override val heatingRod: Item by register(ECRModIDs.HEATING_ROD)
    override val mithrilineCrystalGem: Item by register(ECRModIDs.MITHRILINE_CRYSTAL_GEM)
    override val mruResonatingCrystal: Item by register(ECRModIDs.MRU_RESONATING_CRYSTAL)
    override val fadingCrystal: Item by register(ECRModIDs.FADING_CRYSTAL)
    override val eyeOfAbsorption: Item by register(ECRModIDs.EYE_OF_ABSORPTION)
    override val heatCore: Item by register(ECRModIDs.HEAT_CORE)
    override val monocle: Item by register(ECRModIDs.MONOCLE)

    private fun register(id: String, properties: () -> Item.Properties = Item::Properties) = register(id, ::Item, properties)

    private fun <I: Item> register(id: String, item: (Item.Properties) -> I, properties: () -> Item.Properties = Item::Properties): DeferredItem<I> {
        val itemKey = { it: Identifier -> ResourceKey.create(Registries.ITEM, it) }
        return items.register(id) { rk -> item(properties().setId(itemKey(rk))) }
    }
}
