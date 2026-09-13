package com.algorithmlx.ecr.api.item

import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

interface NoConsumeBreakItem {
    fun onConsume(level: Level, user: LivingEntity, original: ItemStack): ItemStack
}
