package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.utils.ecRL
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

data class MIMCraftPayload(
    val containerId: Int, val expectedItemStack: ItemStack, val batches: Int
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<MIMCraftPayload>("mim_craft".ecRL)
        @JvmField val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MIMCraftPayload> = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MIMCraftPayload::containerId,
            ItemStack.STREAM_CODEC, MIMCraftPayload::expectedItemStack,
            ByteBufCodecs.VAR_INT, MIMCraftPayload::batches,
            ::MIMCraftPayload
        )

        fun handle(player: ServerPlayer, payload: MIMCraftPayload) {
            val menu = player.containerMenu as? MIMMenu ?: return
            if (menu.containerId == payload.containerId)
                menu.requestCraft(player, payload.expectedItemStack, payload.batches)
        }
    }
}
