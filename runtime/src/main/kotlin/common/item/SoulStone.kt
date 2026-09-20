package com.algorithmlx.ecr.common.item

import com.algorithmlx.ecr.api.item.ModifiableSizeItem
import com.algorithmlx.ecr.api.item.SoulStoneLike
import com.algorithmlx.ecr.common.components.SoulStoneComponent
import com.algorithmlx.ecr.registry.DataComponentRegistry
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack

class SoulStone(
    properties: Properties,
) : Item(
        properties.component(
            DataComponentRegistry.soulStone.get(),
            SoulStoneComponent.EMPTY,
        ),
    ),
    SoulStoneLike,
    ModifiableSizeItem {
    override fun inventoryTick(
        itemStack: ItemStack,
        level: ServerLevel,
        owner: Entity,
        slot: EquipmentSlot?,
    ) {
        if (itemStack.count > 1 || owner !is ServerPlayer) return

        val component = itemStack.getOrDefault(DataComponentRegistry.soulStone.get(), SoulStoneComponent.EMPTY)

        if (component == SoulStoneComponent.EMPTY) {
            val bound = SoulStoneComponent(owner.uuid, owner.name.string)
            itemStack.set(DataComponentRegistry.soulStone.get(), bound)
            return
        }

        if (owner.uuid != component.owner || owner.name.string == component.ownerName) return

        itemStack.set(DataComponentRegistry.soulStone.get(), component.copy(ownerName = owner.name.string))
    }

    override val receiveCount: Int = 1
    override val extractCount: Int = 10

    override fun maxStackSize(
        itemStack: ItemStack,
        originalSize: Int,
    ): Int {
        if (itemStack.has(DataComponentRegistry.soulStone.get()) &&
            itemStack[DataComponentRegistry.soulStone.get()] != SoulStoneComponent.EMPTY
        ) {
            return 1
        }
        return originalSize
    }
}
