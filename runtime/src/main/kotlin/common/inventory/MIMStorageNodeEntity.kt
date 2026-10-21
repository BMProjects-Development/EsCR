package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.inventory.InventoryEndpoint
import com.algorithmlx.ecr.api.inventory.InventoryAccess
import com.algorithmlx.ecr.api.item.BoundGemTarget
import com.algorithmlx.ecr.api.item.BoundGemTooltipProvider
import com.algorithmlx.ecr.api.inventory.InventoryNetwork
import com.algorithmlx.ecr.api.mim.MIMStorageNodeProvider
import com.algorithmlx.ecr.api.mim.MIMModules
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState

class MIMStorageNodeEntity(pos: BlockPos, state: BlockState) : BlockEntity(
    BlockEntityTypeRegistry.mimStorageNode.get(), pos, state
), BoundGemTarget, MIMStorageNodeProvider, BoundGemTooltipProvider {
    override fun endpoints(): List<InventoryEndpoint> {
        val level = level ?: return emptyList()
        if (isRemoved || !level.isLoaded(blockPos) || level.getBlockEntity(blockPos) !== this) return emptyList()
        return Direction.entries.mapNotNull { direction ->
            InventoryAccess.resolve(level, blockPos.relative(direction), direction.opposite)
                ?.takeUnless { endpoint -> endpoint.identities.any(MIMModules::isInternal) }
        }
    }

    override fun boundGemTooltip(): List<Component> = buildList {
        val endpoints = endpoints()
        add(Component.translatable("tooltip.escr.mim.storage_node"))
        add(Component.translatable("tooltip.escr.mim.inventories", endpoints.size, Direction.entries.size).withStyle(ChatFormatting.GRAY))
        if (InventoryNetwork(endpoints).isFull()) add(Component.translatable("tooltip.escr.mim.full").withStyle(ChatFormatting.RED))
    }
}
