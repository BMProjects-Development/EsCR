package com.algorithmlx.ecr.fabric.network

import com.algorithmlx.ecr.api.network.ClientNetworkPlatform
import com.algorithmlx.ecr.api.network.ClientPacketContext
import com.algorithmlx.ecr.api.network.ClientboundPacket
import com.algorithmlx.ecr.api.network.PacketRegistry
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

object FabricClientNetworkPlatform : ClientNetworkPlatform {
    override fun install(registry: PacketRegistry) {
        registry.packets.filterIsInstance<ClientboundPacket<*>>().forEach(::registerClientHandler)
    }

    override fun sendToServer(payload: CustomPacketPayload) = ClientPlayNetworking.send(payload)

    @Suppress("UNCHECKED_CAST")
    private fun registerClientHandler(packet: ClientboundPacket<*>) {
        val typed = packet as ClientboundPacket<CustomPacketPayload>
        val handler = checkNotNull(typed.handler) { "Missing client handler for ${typed.type.id()}" }
        ClientPlayNetworking.registerGlobalReceiver(typed.type) { payload, context ->
            context.client().execute { handler(ClientPacketContext, payload) }
        }
    }
}
