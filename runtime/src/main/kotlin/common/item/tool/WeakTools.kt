package com.algorithmlx.ecr.common.item.tool

import com.algorithmlx.ecr.api.mru.MRUMultiplierWeapon
import com.algorithmlx.ecr.common.item.material.ECToolMaterials
import net.minecraft.world.item.Item

class WeakAxe(
    properties: Properties,
) : Item(properties.axe(ECToolMaterials.WEAK.material, 5F, -3.2F))

class WeakHoe(
    properties: Properties,
) : Item(properties.hoe(ECToolMaterials.WEAK.material, -6F, 2F))

class WeakPickaxe(
    properties: Properties,
) : Item(
        properties.pickaxe(ECToolMaterials.WEAK.material, -3F, -2.8F),
    )

class WeakShovel(
    properties: Properties,
) : Item(properties.shovel(ECToolMaterials.WEAK.material, -2.5F, -3F))

class WeakSword(
    properties: Properties,
) : Item(
        properties.sword(ECToolMaterials.WEAK.material, -1F, -2.4F),
    ),
    MRUMultiplierWeapon {
    override val multiplier: Float = 1.2f
}
