package com.algorithmlx.ecr.api.container.slot

import net.minecraft.world.Container
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class VanillaSpecialSlot(
    container: Container,
    index: Int,
    x: Int,
    y: Int,
    private val place: (VanillaSpecialSlot.(ItemStack) -> Boolean)? = null,
    private val pickup: (VanillaSpecialSlot.(Player) -> Boolean)? = null,
    private val stackSize: Int? = null,
    private val stackSizeWithItem: (VanillaSpecialSlot.(ItemStack) -> Int)? = null,
    private val isHighlightable: (VanillaSpecialSlot.() -> Boolean)? = null,
    private val active: (VanillaSpecialSlot.() -> Boolean)? = null
) : Slot(container, index, x, y) {
    override fun getMaxStackSize(): Int = this.stackSize ?: super.getMaxStackSize()

    override fun getMaxStackSize(stack: ItemStack): Int = this.stackSizeWithItem?.let { it(stack) } ?: super.getMaxStackSize(stack)

    override fun mayPlace(stack: ItemStack): Boolean = this.place?.let { it(stack) } ?: super.mayPlace(stack)

    override fun mayPickup(player: Player): Boolean = this.pickup?.let { it(player) } ?: super.mayPickup(player)

    override fun isHighlightable(): Boolean = this.isHighlightable?.let { it() } ?: super.isHighlightable()

    override fun isActive(): Boolean = this.active?.let { it() } ?: super.isActive()

}
