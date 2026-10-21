package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.inventory.InventoryEndpoint
import com.algorithmlx.ecr.api.inventory.InventoryAccess
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.inventory.InventoryNetwork
import com.algorithmlx.ecr.api.item.BoundGemTooltipProvider
import com.algorithmlx.ecr.api.mim.MIMModules
import com.algorithmlx.ecr.api.mim.MIMStorageProvider
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import com.algorithmlx.ecr.common.api.BoundGemHelper
import com.algorithmlx.ecr.common.menu.MIMMenuKind
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

class MIMStorageEntity(pos: BlockPos, state: BlockState) : MIMModuleEntity(
    pos, state, BlockEntityTypeRegistry.mimStorage.get(), MIMMenuKind.STORAGE.slots
), MIMStorageProvider, BoundGemTooltipProvider {
    override val menuKind get() = MIMMenuKind.STORAGE
    override val previousInventorySize: Int get() = 54
    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean {
        if (slot !in 0 until containerSize || stack.item !is BoundGem || !MIMLinks.isBound(stack)) return false
        if (stack.item is InventoryGemItem) return true
        return level?.let { world -> MIMLinks.resolve(world, stack)?.let(MIMModules::inventory) != null } ?: true
    }
    override fun getMaxStackSize(): Int = 1

    override fun endpoints(): List<InventoryEndpoint> {
        val level = level ?: return emptyList()
        return (0 until containerSize).flatMap { slot ->
            val gem = getItem(slot)
            if (!MIMLinks.isBound(gem)) return@flatMap emptyList()
            if (gem.item !is InventoryGemItem)
                return@flatMap MIMLinks.resolve(level, gem)?.let(MIMModules::inventory)?.endpoints().orEmpty()
            val dimension = BoundGemHelper.getLevelKey(gem)
            if (dimension != null && dimension != level.dimension()) return@flatMap emptyList()
            val pos = BoundGemHelper.getBoundPos(gem) ?: return@flatMap emptyList()
            val endpoint = InventoryAccess.resolve(level, pos) ?: return@flatMap emptyList()
            listOfNotNull(endpoint.takeUnless { it.identities.any(MIMModules::isInternal) })
        }
    }

    override fun boundGemTooltip(): List<Component> = buildList {
        add(Component.translatable("tooltip.escr.mim.storage"))
        add(Component.translatable("tooltip.escr.mim.nodes", (0 until containerSize).count { !getItem(it).isEmpty }, containerSize).withStyle(ChatFormatting.GRAY))
        if (InventoryNetwork(endpoints()).isFull()) add(Component.translatable("tooltip.escr.mim.full").withStyle(ChatFormatting.RED))
    }
}
