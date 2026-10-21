package com.algorithmlx.ecr.api.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer

@JvmInline
value class MaxPayloadSize(val bytes: Int) {
    init {
        require(bytes > 0) { "Maximum payload size must be positive" }
    }
}

enum class PacketDirection {
    CLIENTBOUND,
    SERVERBOUND
}

sealed interface PacketContext {
    val direction: PacketDirection
}

data object ClientPacketContext : PacketContext {
    override val direction = PacketDirection.CLIENTBOUND
}

data class ServerPacketContext(val player: ServerPlayer) : PacketContext {
    override val direction = PacketDirection.SERVERBOUND
}

sealed class PacketType<P : CustomPacketPayload>(
    val type: CustomPacketPayload.Type<P>,
    val codec: StreamCodec<in RegistryFriendlyByteBuf, P>,
    val direction: PacketDirection,
    val maxPayloadSize: MaxPayloadSize?
)

class ClientboundPacket<P : CustomPacketPayload> internal constructor(
    type: CustomPacketPayload.Type<P>,
    codec: StreamCodec<in RegistryFriendlyByteBuf, P>,
    maxPayloadSize: MaxPayloadSize?
) : PacketType<P>(type, codec, PacketDirection.CLIENTBOUND, maxPayloadSize) {
    var handler: (ClientPacketContext.(P) -> Unit)? = null
        private set

    fun handle(handler: ClientPacketContext.(P) -> Unit): ClientboundPacket<P> = apply {
        check(this.handler == null) { "Client handler for ${type.id()} is already registered" }
        this.handler = handler
    }
}

class ServerboundPacket<P : CustomPacketPayload> internal constructor(
    type: CustomPacketPayload.Type<P>,
    codec: StreamCodec<in RegistryFriendlyByteBuf, P>,
    val handler: ServerPacketContext.(P) -> Unit,
    maxPayloadSize: MaxPayloadSize?
) : PacketType<P>(type, codec, PacketDirection.SERVERBOUND, maxPayloadSize)
