package com.algorithmlx.ecr.api.item

import net.minecraft.world.item.ItemStack

interface HasSubItem {
    fun addSubItems(original: ItemStack): List<ItemStack>
}
