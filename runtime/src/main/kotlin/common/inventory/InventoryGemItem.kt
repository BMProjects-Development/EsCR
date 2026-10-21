package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.item.NoTab
import com.algorithmlx.ecr.common.api.BoundGemHelper
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class InventoryGemItem(properties: Properties) : Item(properties), BoundGem, NoTab {
    override val dimensionalBounds = false
    override fun getBoundPos(stack: ItemStack): BlockPos? = BoundGemHelper.getBoundPos(stack)
    override fun setBoundPos(stack: ItemStack, blockPos: BlockPos?) = BoundGemHelper.setBoundPos(stack, blockPos)
    override fun getWorld(stack: ItemStack): ResourceKey<Level>? = BoundGemHelper.getLevelKey(stack)
    override fun setWorld(stack: ItemStack, world: ResourceKey<Level>?) = BoundGemHelper.setLevelKey(stack, world)
}
