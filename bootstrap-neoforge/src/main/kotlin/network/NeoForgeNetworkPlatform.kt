package com.algorithmlx.ecr.neoforge.network

import com.algorithmlx.ecr.api.network.ClientboundPacket
import com.algorithmlx.ecr.api.network.NetworkPlatform
import com.algorithmlx.ecr.api.network.PacketRegistry
import com.algorithmlx.ecr.api.network.ServerPacketContext
import com.algorithmlx.ecr.api.network.ServerboundPacket
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent

class NeoForgeNetworkPlatform(bus: IEventBus) : NetworkPlatform {
    private val registries = mutableListOf<PacketRegistry>()

    init {
        bus.addListener(::registerPayloads)
    }

    override fun install(registry: PacketRegistry) {
        check(registry !in registries) { "Packet registry '${registry.namespace}' is already installed" }
        registries += registry
    }

    override fun sendToPlayer(player: ServerPlayer, payload: CustomPacketPayload) =
        PacketDistributor.sendToPlayer(player, payload)

    private fun registerPayloads(event: RegisterPayloadHandlersEvent) {
        registries.forEach { registry ->
            val registrar = event.registrar(registry.namespace)
            registry.packets.forEach { packet ->
                when (packet) {
                    is ClientboundPacket<*> -> registrar.playToClient(packet.type, packet.codec)
                    is ServerboundPacket<*> -> registrar.playToServer(packet.type, packet.codec) { payload, context ->
                        context.enqueueWork {
                            (context.player() as? ServerPlayer)?.let { player ->
                                packet.handler(ServerPacketContext(player), payload)
                            }
                        }
                    }
                }
            }
        }
    }
}
