package com.algorithmlx.ecr.client.utils

import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.network.sendToServer
import com.algorithmlx.ecr.network.MenuBoundGemTooltipRequest
import com.algorithmlx.ecr.network.MenuBoundGemTooltipResponse
import net.minecraft.ChatFormatting
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.minecraft.util.Util
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

object BoundGemSlotTooltips {
    private const val REFRESH_MS = 250L
    private var owner: AbstractContainerMenu? = null
    private var sequence = 0
    private val cache = hashMapOf<Int, Entry>()
    private val pending = hashMapOf<Int, Request>()

    fun clear() {
        owner = null
        cache.clear()
        pending.clear()
    }

    fun tick() {
        if (owner !== Minecraft.getInstance().player?.containerMenu) clear()
    }

    @JvmStatic
    fun lines(menu: AbstractContainerMenu, slot: Slot?, stack: ItemStack): List<Component>? {
        if (slot == null || slot.container is Inventory || !slot.isActive || stack.item !is BoundGem) return null
        if (owner !== menu) {
            owner = menu
            cache.clear()
            pending.clear()
        }
        val now = Util.getMillis()
        val entry = cache[slot.index]?.takeIf { ItemStack.matches(it.stack, stack) }
        if (entry == null || now - entry.updatedAt >= REFRESH_MS) {
            val request = pending.values.firstOrNull { it.slot == slot.index && now - it.sentAt < REFRESH_MS }
            if (request == null) {
                pending.entries.removeIf { now - it.value.sentAt >= REFRESH_MS }
                val id = ++sequence
                pending[id] = Request(slot.index, stack.copy(), now)
                MenuBoundGemTooltipRequest(menu.containerId, slot.index, id).sendToServer()
            }
        }
        if (entry?.contextual != true) return null
        return buildList {
            addAll(entry.lines)
            val coordinates = (stack.item as BoundGem).getBoundPos(stack)
            if (Minecraft.getInstance().hasShiftDown() && coordinates != null)
                add(1.coerceAtMost(size), Component.translatable("tooltip.escr.bound_gem.coordinates", coordinates.x, coordinates.y, coordinates.z).withStyle(ChatFormatting.GRAY))
        }
    }

    fun accept(response: MenuBoundGemTooltipResponse) {
        val request = pending.remove(response.requestId) ?: return
        val menu = owner ?: return
        if (Minecraft.getInstance().player?.containerMenu !== menu || !ItemStack.matches(request.stack, response.stack)) return
        val slot = menu.slots.getOrNull(request.slot) ?: return
        if (!ItemStack.matches(slot.item, response.stack)) return
        cache[request.slot] = Entry(response.stack.copy(), response.contextual, response.lines.map(Component::copy), Util.getMillis())
    }

    private class Request(val slot: Int, val stack: ItemStack, val sentAt: Long)
    private class Entry(val stack: ItemStack, val contextual: Boolean, val lines: List<Component>, val updatedAt: Long)
}
