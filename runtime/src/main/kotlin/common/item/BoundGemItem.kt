package com.algorithmlx.ecr.common.item

import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.item.ModifiableSizeItem
import com.algorithmlx.ecr.common.api.BoundGemHelper
import com.algorithmlx.ecr.registry.DataComponentRegistry
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

class BoundGemItem(
    properties: Properties
) : Item(properties),
    BoundGem,
    ModifiableSizeItem {
    override val dimensionalBounds: Boolean = false

    override fun getBoundPos(stack: ItemStack): BlockPos? = BoundGemHelper.getBoundPos(stack)

    override fun setBoundPos(
        stack: ItemStack,
        blockPos: BlockPos?
    ) = BoundGemHelper.setBoundPos(stack, blockPos)

    override fun getWorld(stack: ItemStack): ResourceKey<Level>? = BoundGemHelper.getLevelKey(stack)

    override fun setWorld(
        stack: ItemStack,
        world: ResourceKey<Level>?
    ) = BoundGemHelper.setLevelKey(stack, world)

    override fun isOutsideBoundRadius(stack: ItemStack): Boolean = BoundGemHelper.isOutsideBoundRadius(stack)

    override fun setOutsideBoundRadius(
        stack: ItemStack,
        outside: Boolean
    ): Boolean = BoundGemHelper.setOutsideBoundRadius(stack, outside)

    override fun maxStackSize(
        itemStack: ItemStack,
        originalSize: Int
    ): Int {
        if (itemStack[DataComponentRegistry.boundGem.get()] != null) return 1
        return originalSize
    }
}
