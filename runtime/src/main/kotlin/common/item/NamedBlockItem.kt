package com.algorithmlx.ecr.common.item

import net.minecraft.network.chat.Component
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block

class NamedBlockItem(
    block: Block,
    properties: Properties
) : BlockItem(block, properties) {
    override fun getName(itemStack: ItemStack): Component = this.block.name
}
