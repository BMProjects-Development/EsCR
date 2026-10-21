package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.utils.ecRL
import io.netty.handler.codec.DecoderException
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerInput

data class MIMCoreClickPayload(
    val containerId: Int, val row: Int, val slot: Int, val button: Int, val input: ContainerInput
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        private val INPUT_CODEC = ByteBufCodecs.idMapper(
            { id: Int -> ContainerInput.entries.getOrNull(id) ?: throw DecoderException("Unknown container input: $id") },
            { input: ContainerInput -> input.ordinal }
        )
        @JvmField val TYPE = CustomPacketPayload.Type<MIMCoreClickPayload>("mim_core_click".ecRL)
        @JvmField val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MIMCoreClickPayload> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MIMCoreClickPayload::containerId,
            ByteBufCodecs.VAR_INT, MIMCoreClickPayload::row,
            ByteBufCodecs.VAR_INT, MIMCoreClickPayload::slot,
            ByteBufCodecs.VAR_INT, MIMCoreClickPayload::button,
            INPUT_CODEC, MIMCoreClickPayload::input,
            ::MIMCoreClickPayload
        )

        fun handle(player: ServerPlayer, payload: MIMCoreClickPayload) {
            val menu = player.containerMenu as? MIMMenu ?: return
            if (menu.containerId != payload.containerId || !menu.isCore || !menu.stillValid(player) || !validButton(payload, player)) return
            player.resetLastActionTime()
            menu.clickCore(player, payload.row, payload.slot, payload.button, payload.input)
        }

        private fun validButton(payload: MIMCoreClickPayload, player: ServerPlayer): Boolean = when (payload.input) {
            ContainerInput.PICKUP, ContainerInput.QUICK_MOVE, ContainerInput.THROW, ContainerInput.PICKUP_ALL -> payload.button in 0..1
            ContainerInput.SWAP -> payload.button in 0..8 || payload.button == 40
            ContainerInput.CLONE -> payload.button == 0 || payload.button == 2
            ContainerInput.QUICK_CRAFT -> AbstractContainerMenu.getQuickcraftHeader(payload.button) in 0..2 &&
                AbstractContainerMenu.isValidQuickcraftType(AbstractContainerMenu.getQuickcraftType(payload.button), player)
        }
    }
}
