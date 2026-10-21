package com.algorithmlx.ecr.api.item

import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

interface BoundGem {
    val world: String? get() = null

    val dimensionalBounds: Boolean get() = true

    val transferStrength: Int get() = 1000

    val boundRadius: Double get() = 16.0

    fun getBoundPos(stack: ItemStack): BlockPos?

    fun setBoundPos(stack: ItemStack, blockPos: BlockPos?)

    fun getWorld(stack: ItemStack): ResourceKey<Level>?

    fun setWorld(stack: ItemStack, world: ResourceKey<Level>?)

    fun isOutsideBoundRadius(stack: ItemStack): Boolean = false

    fun setOutsideBoundRadius(stack: ItemStack, outside: Boolean): Boolean = false

    fun isWithinBoundRadius(from: BlockPos, to: BlockPos): Boolean {
        val radius = boundRadius
        if (radius < 0.0 || radius.isNaN()) return false

        val x = (to.x - from.x).toDouble()
        val y = (to.y - from.y).toDouble()
        val z = (to.z - from.z).toDouble()
        return x * x + y * y + z * z <= radius * radius
    }
}
