package com.algorithmlx.ecr.api.inventory

import com.algorithmlx.ecr.api.inventory.ItemStorageIdentity
import com.algorithmlx.ecr.api.inventory.PlatformItemContainer
import com.algorithmlx.ecr.api.mixin.CompoundContainerAccessor
import net.minecraft.world.CompoundContainer
import net.minecraft.world.Container

internal class InventorySlot(val container: Container, val index: Int) {
    private val identity = (container as? PlatformItemContainer)?.slotIdentity(index) ?: ItemStorageIdentity(container, index)
    override fun equals(other: Any?): Boolean =
        other is InventorySlot && identity == other.identity

    override fun hashCode(): Int = identity.hashCode()

    companion object {
        fun path(container: Container, index: Int): List<InventorySlot> = buildList {
            var current = container
            var slot = index
            add(InventorySlot(current, slot))
            while (current is CompoundContainer) {
                val compound = current as CompoundContainerAccessor
                val first = compound.container1()
                if (slot < first.containerSize)
                    current = first
                else {
                    slot -= first.containerSize
                    current = compound.container2()
                }
                add(InventorySlot(current, slot))
            }
        }

        fun containers(container: Container): List<Container> = if (container is CompoundContainer) {
            val compound = container as CompoundContainerAccessor
            containers(compound.container1()) + containers(compound.container2())
        }
        else
            listOf(container)
    }
}
