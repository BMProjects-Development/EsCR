package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.registries.CreativeTabBuilder
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.init.ECRModIDs
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.ItemStack

object CreativeTabRegistry : RegistrationHandler(ModId) {
    private val itemsKey = ResourceKey.create(
        Registries.CREATIVE_MODE_TAB,
        ECRModIDs.TAB_ITEMS.ecRL
    )
    val items = registerCreativeTab(ECRModIDs.TAB_ITEMS) {
        CreativeTabBuilder()
            .icon { ItemStack(ItemRegistry.elementalGem.get()) }
            .title(Component.translatable("itemGroup.$ModId.items"))
            .build()
    }

    val blocks = registerCreativeTab(ECRModIDs.TAB_BLOCKS) {
        CreativeTabBuilder()
            .icon { ItemStack(BlockRegistry.mithrilineFurnace.get()) }
            .title(Component.translatable("itemGroup.$ModId.blocks"))
            .withTabsBefore(itemsKey)
            .build()
    }
}
