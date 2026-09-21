package com.algorithmlx.ecr.api.network

import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer

interface NetworkPlatform {
    fun install(registry: PacketRegistry)

    fun sendToPlayer(player: ServerPlayer, payload: CustomPacketPayload)

    companion object {
        @JvmStatic
        lateinit var instance: NetworkPlatform
    }
}

interface ClientNetworkPlatform {
    fun install(registry: PacketRegistry)

    fun sendToServer(payload: CustomPacketPayload)

    companion object {
        @JvmStatic
        lateinit var instance: ClientNetworkPlatform
    }
}

object Network {
    @JvmStatic
    fun sendToServer(payload: CustomPacketPayload) = ClientNetworkPlatform.instance.sendToServer(payload)

    @JvmStatic
    fun sendTo(player: ServerPlayer, payload: CustomPacketPayload) =
        NetworkPlatform.instance.sendToPlayer(player, payload)

    @JvmStatic
    fun sendTo(players: Iterable<ServerPlayer>, payload: CustomPacketPayload) =
        players.forEach { player -> sendTo(player, payload) }

    @JvmStatic
    fun sendToAll(server: MinecraftServer, payload: CustomPacketPayload) =
        sendTo(server.playerList.players, payload)
}

fun CustomPacketPayload.sendToServer() = Network.sendToServer(this)

infix fun CustomPacketPayload.sendTo(player: ServerPlayer) = Network.sendTo(player, this)

infix fun CustomPacketPayload.sendTo(players: Iterable<ServerPlayer>) = Network.sendTo(players, this)

infix fun ServerPlayer.send(payload: CustomPacketPayload) = Network.sendTo(this, payload)
