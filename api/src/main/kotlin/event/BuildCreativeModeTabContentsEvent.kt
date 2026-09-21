package com.algorithmlx.ecr.api.event

import com.algorithmlx.ecr.api.event.engine.Event
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike

class BuildCreativeModeTabContentsEvent(
    val tab: CreativeModeTab,
    private val output: (ItemStack) -> Unit
) : Event() {
    fun accept(item: ItemLike) = accept(ItemStack(item))

    fun accept(stack: ItemStack) = output(stack)
}
