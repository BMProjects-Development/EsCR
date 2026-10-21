package com.algorithmlx.ecr.api.item

import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.resolveMRUDevice
import com.algorithmlx.ecr.api.container.AbstractMenu
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

fun interface BoundGemTooltipProvider {
    fun boundGemTooltip(): List<Component>
}

interface BoundGemSlotContext {
    fun hasBoundGemTooltip(slot: Slot): Boolean
}

object BoundGemTooltips {
    private val providers = linkedMapOf<Identifier, (Level, BlockPos) -> List<Component>?>()

    @JvmStatic
    fun register(id: Identifier, provider: (Level, BlockPos) -> List<Component>?) {
        check(providers.putIfAbsent(id, provider) == null) { "Duplicate Bound Gem tooltip provider: $id" }
    }

    @JvmStatic
    fun isContextual(menu: AbstractContainerMenu, slot: Slot): Boolean {
        if (slot.container is Inventory || slot.item.item !is BoundGem) return false
        if ((menu as? BoundGemSlotContext)?.hasBoundGemTooltip(slot) == true) return true
        var device = slot.container as? MRUDevice
        if (device == null)
            (menu as? AbstractMenu)?.access?.execute { level, pos -> device = level.resolveMRUDevice(pos) }
        val locator = device?.locator ?: return false
        return locator.locatorStorage === slot.container && locator.locatorSlot == slot.containerSlot
    }

    @JvmStatic
    fun lines(player: ServerPlayer, slot: Slot): List<Component> {
        val stack = slot.item
        val gem = stack.item as? BoundGem ?: return emptyList()
        val pos = gem.getBoundPos(stack) ?: return unavailable()
        val dimension = gem.getWorld(stack)
        val level = if (dimension == null) player.level() else player.level().server.getLevel(dimension) ?: return unavailable()
        if (!level.isLoaded(pos) || level.getBlockState(pos).isAir) return unavailable()
        return target(level, pos) + if (slot.mayPlace(stack)) emptyList()
        else listOf(Component.translatable("tooltip.escr.bound_gem.incompatible").withStyle(ChatFormatting.RED))
    }

    @JvmStatic
    fun target(level: Level, pos: BlockPos): List<Component> {
        if (!level.isLoaded(pos) || level.getBlockState(pos).isAir) return unavailable()
        val custom = (level.getBlockEntity(pos) as? BoundGemTooltipProvider)?.boundGemTooltip()
            ?: providers.values.firstNotNullOfOrNull { it(level, pos) }
        return buildList {
            addAll(custom?.takeIf { it.isNotEmpty() } ?: listOf(level.getBlockState(pos).block.name))
            val storage = level.resolveMRUDevice(pos)?.mruStorage
            if (storage != null && storage.mruCapacity > 0)
                add(Component.translatable("tooltip.escr.bound_gem.mru", storage.mru, storage.mruCapacity).withStyle(ChatFormatting.GRAY))
        }
    }

    private fun unavailable(): List<Component> = listOf(Component.translatable("tooltip.escr.bound_gem.unavailable"))
}
