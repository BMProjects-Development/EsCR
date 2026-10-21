package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.utils.ecRL
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer

data class MIMViewportPayload(val containerId: Int, val maxRows: Int, val row: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<MIMViewportPayload>("mim_viewport".ecRL)
        @JvmField val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MIMViewportPayload> = StreamCodec.of(
            { buffer, value ->
                buffer.writeVarInt(value.containerId)
                buffer.writeVarInt(value.maxRows)
                buffer.writeVarInt(value.row)
            },
            { buffer -> MIMViewportPayload(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()) }
        )

        fun handle(player: ServerPlayer, payload: MIMViewportPayload) {
            val menu = player.containerMenu as? MIMMenu ?: return
            if (menu.containerId == payload.containerId) menu.setViewport(player, payload.maxRows, payload.row)
        }
    }
}
