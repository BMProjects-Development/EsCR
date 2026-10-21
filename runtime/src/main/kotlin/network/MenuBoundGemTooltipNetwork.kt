package com.algorithmlx.ecr.network

import com.algorithmlx.ecr.api.item.BoundGemTooltips
import com.algorithmlx.ecr.api.network.Network
import com.algorithmlx.ecr.api.utils.ecRL
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.ComponentSerialization
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

data class MenuBoundGemTooltipRequest(val containerId: Int, val slot: Int, val requestId: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MenuBoundGemTooltipRequest>("menu_bound_gem_tooltip_request".ecRL)
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MenuBoundGemTooltipRequest> = StreamCodec.of(
            { buffer, value -> buffer.writeVarInt(value.containerId); buffer.writeVarInt(value.slot); buffer.writeVarInt(value.requestId) },
            { buffer -> MenuBoundGemTooltipRequest(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()) }
        )

        fun handle(player: ServerPlayer, request: MenuBoundGemTooltipRequest) {
            val menu = player.containerMenu
            if (menu.containerId != request.containerId || !menu.stillValid(player)) return
            val slot = menu.slots.getOrNull(request.slot) ?: return
            if (!slot.isActive) return
            val contextual = BoundGemTooltips.isContextual(menu, slot)
            val lines = if (contextual) BoundGemTooltips.lines(player, slot) else emptyList()
            Network.sendTo(player, MenuBoundGemTooltipResponse(request.requestId, slot.item.copy(), contextual, lines))
        }
    }
}

data class MenuBoundGemTooltipResponse(
    val requestId: Int, val stack: ItemStack, val contextual: Boolean, val lines: List<Component>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        private const val MAX_LINES = 128
        val TYPE = CustomPacketPayload.Type<MenuBoundGemTooltipResponse>("menu_bound_gem_tooltip_response".ecRL)
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MenuBoundGemTooltipResponse> = StreamCodec.of(
            { buffer, value ->
                buffer.writeVarInt(value.requestId)
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, value.stack)
                buffer.writeBoolean(value.contextual)
                buffer.writeVarInt(value.lines.size.coerceAtMost(MAX_LINES))
                value.lines.take(MAX_LINES).forEach { ComponentSerialization.STREAM_CODEC.encode(buffer, it) }
            },
            { buffer ->
                val requestId = buffer.readVarInt()
                val stack = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)
                val contextual = buffer.readBoolean()
                val count = buffer.readVarInt()
                require(count in 0..MAX_LINES) { "Invalid Bound Gem tooltip line count: $count" }
                MenuBoundGemTooltipResponse(requestId, stack, contextual, List(count) { ComponentSerialization.STREAM_CODEC.decode(buffer) })
            }
        )
    }
}
