package com.algorithmlx.ecr.api.item

import net.minecraft.world.item.ItemStack

interface ModifiableSizeItem {
    fun maxStackSize(
        itemStack: ItemStack,
        originalSize: Int
    ): Int
}
