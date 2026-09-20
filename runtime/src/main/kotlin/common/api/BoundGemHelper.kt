package com.algorithmlx.ecr.common.api

import com.algorithmlx.ecr.api.container.slot.VanillaSpecialSlot
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.common.components.BoundGemComponent
import com.algorithmlx.ecr.registry.DataComponentRegistry
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import java.util.*
import kotlin.jvm.optionals.getOrNull

object BoundGemHelper {
    @JvmStatic
    fun getBoundPos(stack: ItemStack): BlockPos? = stack.get(DataComponentRegistry.boundGem.get())?.pos

    @JvmStatic
    fun setBoundPos(stack: ItemStack, pos: BlockPos?) {
        val data = stack.get(DataComponentRegistry.boundGem.get())
        if (pos != null) stack.set(
            DataComponentRegistry.boundGem.get(),
            data?.copy(pos = pos, outsideBoundRadius = false) ?: BoundGemComponent(pos)
        ) else stack.set(DataComponentRegistry.boundGem.get(), null)
    }

    @JvmStatic
    fun setLevelKey(stack: ItemStack, world: ResourceKey<Level>?) {
        val data = stack.get(DataComponentRegistry.boundGem.get()) ?: return
        stack.set(
            DataComponentRegistry.boundGem.get(),
            data.copy(dimension = Optional.ofNullable(world), outsideBoundRadius = false)
        )
    }

    @JvmStatic
    fun getLevelKey(stack: ItemStack): ResourceKey<Level>? = stack.get(DataComponentRegistry.boundGem.get())
        ?.dimension
        ?.getOrNull()

    @JvmStatic
    fun isOutsideBoundRadius(stack: ItemStack): Boolean =
        stack.get(DataComponentRegistry.boundGem.get())?.outsideBoundRadius == true

    @JvmStatic
    fun setOutsideBoundRadius(stack: ItemStack, outside: Boolean): Boolean {
        val data = stack.get(DataComponentRegistry.boundGem.get()) ?: return false
        if (data.outsideBoundRadius == outside) return false

        stack.set(DataComponentRegistry.boundGem.get(), data.copy(outsideBoundRadius = outside))
        return true
    }

    @JvmStatic
    fun isConnectionFound(stack: ItemStack): Boolean = stack.item is BoundGem && getBoundPos(stack) != null

    @JvmStatic
    fun isConnectionFoundSpecial(@Suppress("unused") encoded: VanillaSpecialSlot, stack: ItemStack) = isConnectionFound(stack)
}
