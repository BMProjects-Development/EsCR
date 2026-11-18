package com.algorithmlx.ecr.neoforge.inventory

import com.algorithmlx.ecr.api.ModId
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.Container
import net.minecraft.world.WorldlyContainer
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent
import net.neoforged.neoforge.transfer.item.VanillaContainerWrapper
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper

object NeoForgeContainerCapabilities {
    fun register(event: RegisterCapabilitiesEvent) {
        for (id in BuiltInRegistries.BLOCK_ENTITY_TYPE.keySet().filter { it.namespace == ModId }) {
            val type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getOptional(id).orElseThrow()
            event.registerBlockEntity(Capabilities.Item.BLOCK, type) { entity, side ->
                when {
                    entity is WorldlyContainer && side != null -> WorldlyContainerWrapper(entity, side)
                    entity is Container -> VanillaContainerWrapper.of(entity)
                    else -> null
                }
            }
        }
    }
}
