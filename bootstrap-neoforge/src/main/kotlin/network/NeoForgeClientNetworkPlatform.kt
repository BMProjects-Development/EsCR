package com.algorithmlx.ecr.neoforge.network

import com.algorithmlx.ecr.api.network.ClientNetworkPlatform
import com.algorithmlx.ecr.api.network.ClientPacketContext
import com.algorithmlx.ecr.api.network.ClientboundPacket
import com.algorithmlx.ecr.api.network.PacketRegistry
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent

class NeoForgeClientNetworkPlatform(bus: IEventBus) : ClientNetworkPlatform {
    private val registries = mutableListOf<PacketRegistry>()

    init {
        bus.addListener(::registerPayloads)
    }

    override fun install(registry: PacketRegistry) {
        check(registry !in registries) { "Client packet registry '${registry.namespace}' is already installed" }
        registries += registry
    }

    override fun sendToServer(payload: CustomPacketPayload) = ClientPacketDistributor.sendToServer(payload)

    private fun registerPayloads(event: RegisterClientPayloadHandlersEvent) {
        registries.asSequence()
            .flatMap { registry -> registry.packets.asSequence() }
            .filterIsInstance<ClientboundPacket<*>>()
            .forEach { packet -> register(event, packet) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun register(event: RegisterClientPayloadHandlersEvent, packet: ClientboundPacket<*>) {
        val typed = packet as ClientboundPacket<CustomPacketPayload>
        val handler = checkNotNull(typed.handler) { "Missing client handler for ${typed.type.id()}" }
        event.register(typed.type) { payload, _ -> handler(ClientPacketContext, payload) }
    }
}
