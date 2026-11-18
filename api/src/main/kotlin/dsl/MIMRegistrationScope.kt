package com.algorithmlx.ecr.api.dsl

import com.algorithmlx.ecr.api.ECRAPIScope
import com.algorithmlx.ecr.api.mim.MIMFilterProvider
import com.algorithmlx.ecr.api.mim.MIMFilters
import com.algorithmlx.ecr.api.mim.MIMFrameProvider
import com.algorithmlx.ecr.api.mim.MIMFrames
import com.algorithmlx.ecr.api.mim.MIMModule
import com.algorithmlx.ecr.api.mim.MIMModules
import com.algorithmlx.ecr.api.mim.MIMStorageNodeProvider
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

class MIMRegistrationScope(namespace: String): ECRAPIScope(namespace) {
    fun module(path: String, adapter: (BlockEntity) -> MIMModule?) = MIMModules.register(id(path), adapter)
    fun inventory(path: String, adapter: (BlockEntity) -> MIMStorageNodeProvider?) = MIMModules.registerInventory(id(path), adapter)
    fun filter(path: String, accepts: (ItemStack) -> Boolean, provider: MIMFilterProvider) = MIMFilters.register(id(path), accepts, provider)
    fun frame(path: String, accepts: (ItemStack) -> Boolean, provider: MIMFrameProvider) = MIMFrames.register(id(path), accepts, provider)
}
