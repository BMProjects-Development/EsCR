package com.algorithmlx.ecr.api.block.entity

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.block.entity.BlockEntity
import kotlin.math.hypot

fun BlockEntity.syncForNearby() {
    val level = this.level ?: return
    val packet = this.updatePacket ?: return

    val players = level.players()
    val pos = this.blockPos

    players.filterIsInstance<ServerPlayer>()
        .filter { hypot(it.x - (pos.x + 0.5), it.z - (pos.z + 0.5)) < 64 }
        .forEach { it.connection.send(packet) }
}
