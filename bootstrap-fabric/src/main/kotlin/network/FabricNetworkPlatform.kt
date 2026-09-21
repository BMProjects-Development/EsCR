package com.algorithmlx.ecr.fabric.network

import com.algorithmlx.ecr.api.network.ClientboundPacket
import com.algorithmlx.ecr.api.network.NetworkPlatform
import com.algorithmlx.ecr.api.network.PacketRegistry
import com.algorithmlx.ecr.api.network.ServerPacketContext
import com.algorithmlx.ecr.api.network.ServerboundPacket
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer

object FabricNetworkPlatform : NetworkPlatform {
    override fun install(registry: PacketRegistry) {
        registry.packets.forEach { packet ->
            when (packet) {
                is ClientboundPacket<*> -> registerClientbound(packet)
                is ServerboundPacket<*> -> registerServerbound(packet)
            }
        }
    }

    override fun sendToPlayer(player: ServerPlayer, payload: CustomPacketPayload) =
        ServerPlayNetworking.send(player, payload)

    private fun <P : CustomPacketPayload> registerClientbound(packet: ClientboundPacket<P>) {
        val size = packet.maxPayloadSize
        if (size == null) {
            PayloadTypeRegistry.clientboundPlay().register(packet.type, packet.codec)
        } else {
            PayloadTypeRegistry.clientboundPlay().registerLarge(packet.type, packet.codec, size.bytes)
        }
    }

    private fun <P : CustomPacketPayload> registerServerbound(packet: ServerboundPacket<P>) {
        val size = packet.maxPayloadSize
        if (size == null) {
            PayloadTypeRegistry.serverboundPlay().register(packet.type, packet.codec)
        } else {
            PayloadTypeRegistry.serverboundPlay().registerLarge(packet.type, packet.codec, size.bytes)
        }
        ServerPlayNetworking.registerGlobalReceiver(packet.type) { payload, context ->
            context.server().execute { packet.handler(ServerPacketContext(context.player()), payload) }
        }
    }
}
