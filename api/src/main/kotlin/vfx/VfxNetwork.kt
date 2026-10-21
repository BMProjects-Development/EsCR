package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.network.Network
import com.algorithmlx.ecr.api.utils.ecRL
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.phys.Vec3
import net.minecraft.world.entity.Entity
import java.util.concurrent.atomic.AtomicInteger

data class VfxCommandPayload(
    val action: Action,
    val handle: Int,
    val effect: Identifier,
    val position: Vec3,
    val entityId: Int,
    val immediate: Boolean,
    val data: Map<String, Float>
) : CustomPacketPayload {
    init {
        require(handle > 0) { "Network VFX handles must be positive" }
        require(position.x.isFinite() && position.y.isFinite() && position.z.isFinite()) { "VFX position must be finite" }
        require(data.size <= 64 && data.all { (key, value) -> key.length <= 128 && value.isFinite() }) { "Invalid VFX data" }
    }

    enum class Action { PLAY, STOP, MOVE, DATA }

    override fun type() = TYPE

    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<VfxCommandPayload>("vfx_command".ecRL)
        @JvmField val STREAM_CODEC: StreamCodec<FriendlyByteBuf, VfxCommandPayload> = StreamCodec.of(
            { buffer, payload ->
                buffer.writeVarInt(payload.action.ordinal)
                buffer.writeVarInt(payload.handle)
                buffer.writeIdentifier(payload.effect)
                buffer.writeDouble(payload.position.x)
                buffer.writeDouble(payload.position.y)
                buffer.writeDouble(payload.position.z)
                buffer.writeVarInt(payload.entityId)
                buffer.writeBoolean(payload.immediate)
                buffer.writeVarInt(payload.data.size)
                payload.data.forEach { (key, value) ->
                    buffer.writeUtf(key, 128)
                    buffer.writeFloat(value)
                }
            },
            { buffer ->
                val action = Action.entries.getOrNull(buffer.readVarInt()) ?: error("Invalid VFX action")
                val handle = buffer.readVarInt()
                val effect = buffer.readIdentifier()
                val position = Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble())
                val entityId = buffer.readVarInt()
                val immediate = buffer.readBoolean()
                val count = buffer.readVarInt()
                require(count in 0..64) { "Too many VFX data fields" }
                val data = buildMap {
                    repeat(count) { put(buffer.readUtf(128), buffer.readFloat()) }
                }
                VfxCommandPayload(action, handle, effect, position, entityId, immediate, data)
            }
        )
    }
}

class VfxHandle internal constructor(val id: Int, private val players: List<ServerPlayer>, private val effect: Identifier) {
    var isStopped = false
        private set
    private var stoppedImmediately = false

    fun stop(immediate: Boolean = false) {
        if (isStopped && (!immediate || stoppedImmediately)) return
        isStopped = true
        stoppedImmediately = immediate
        Network.sendTo(players, VfxCommandPayload(VfxCommandPayload.Action.STOP, id, effect, Vec3.ZERO, -1, immediate, emptyMap()))
    }

    fun move(position: Vec3) {
        if (isStopped) return
        Network.sendTo(players, VfxCommandPayload(VfxCommandPayload.Action.MOVE, id, effect, position, -1, false, emptyMap()))
    }

    fun data(values: Map<String, Float>) {
        if (isStopped) return
        require(values.size <= 64)
        Network.sendTo(players, VfxCommandPayload(VfxCommandPayload.Action.DATA, id, effect, Vec3.ZERO, -1, false, values))
    }
}

object VfxNetwork {
    private val nextHandle = AtomicInteger(1)

    fun play(players: Iterable<ServerPlayer>, effect: Identifier, position: Vec3, data: Map<String, Float> = emptyMap()): VfxHandle {
        return sendPlay(players, effect, position, -1, data)
    }

    fun playAttached(players: Iterable<ServerPlayer>, effect: Identifier, entityId: Int, position: Vec3, data: Map<String, Float> = emptyMap()): VfxHandle {
        return sendPlay(players, effect, position, entityId, data)
    }

    fun playAttached(players: Iterable<ServerPlayer>, effect: Identifier, entity: Entity, offset: Vec3 = Vec3.ZERO, data: Map<String, Float> = emptyMap()): VfxHandle =
        sendPlay(players, effect, entity.position().add(offset), entity.id, data)

    private fun sendPlay(players: Iterable<ServerPlayer>, effect: Identifier, position: Vec3, entityId: Int, data: Map<String, Float>): VfxHandle {
        require(data.size <= 64)
        val recipients = players.toList()
        val handle = nextHandle.getAndIncrement()
        Network.sendTo(recipients, VfxCommandPayload(VfxCommandPayload.Action.PLAY, handle, effect, position, entityId, false, data))
        return VfxHandle(handle, recipients, effect)
    }

}
