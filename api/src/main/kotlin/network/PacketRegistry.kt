package com.algorithmlx.ecr.api.network

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

open class PacketRegistry(val namespace: String) {
    private val mutablePackets = mutableListOf<PacketType<*>>()
    private val packetIds = mutableSetOf<CustomPacketPayload.Type<*>>()
    private var installed = false
    private var clientInstalled = false

    val packets: List<PacketType<*>>
        get() = mutablePackets.toList()

    protected fun <P : CustomPacketPayload> clientbound(
        type: CustomPacketPayload.Type<P>,
        codec: StreamCodec<in RegistryFriendlyByteBuf, P>,
        maxPayloadSize: MaxPayloadSize? = null
    ): ClientboundPacket<P> = declare(ClientboundPacket(type, codec, maxPayloadSize))

    protected fun <P : CustomPacketPayload> serverbound(
        type: CustomPacketPayload.Type<P>,
        codec: StreamCodec<in RegistryFriendlyByteBuf, P>,
        maxPayloadSize: MaxPayloadSize? = null,
        handler: ServerPacketContext.(P) -> Unit
    ): ServerboundPacket<P> = declare(ServerboundPacket(type, codec, handler, maxPayloadSize))

    fun install(): PacketRegistry = apply {
        check(!installed) { "Packet registry '$namespace' is already installed" }
        installed = true
        NetworkPlatform.instance.install(this)
    }

    fun installClient(): PacketRegistry = apply {
        check(installed) { "Packet registry '$namespace' must be installed before its client handlers" }
        check(!clientInstalled) { "Client packet registry '$namespace' is already installed" }
        clientInstalled = true
        ClientNetworkPlatform.instance.install(this)
    }

    private fun <P : PacketType<*>> declare(packet: P): P {
        check(!installed) { "Cannot declare ${packet.type.id()} after packet registry '$namespace' was installed" }
        require(packet.type.id().namespace == namespace) {
            "Packet ${packet.type.id()} does not belong to namespace '$namespace'"
        }
        require(packetIds.add(packet.type)) { "Duplicate packet type ${packet.type.id()}" }
        mutablePackets += packet
        return packet
    }
}
