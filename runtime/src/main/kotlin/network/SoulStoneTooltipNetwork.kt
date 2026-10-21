package com.algorithmlx.ecr.network

import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.api.network.Network
import com.algorithmlx.ecr.api.network.sendToServer
import com.algorithmlx.ecr.common.components.playerMatrix
import net.minecraft.core.UUIDUtil
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

data class SoulStoneTooltipRequestPayload(
    val owner: UUID
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<SoulStoneTooltipRequestPayload>("soul_stone_tooltip_request".ecRL)

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, SoulStoneTooltipRequestPayload> =
            StreamCodec.of(
                { buffer, value -> UUIDUtil.STREAM_CODEC.encode(buffer, value.owner) },
                { buffer -> SoulStoneTooltipRequestPayload(UUIDUtil.STREAM_CODEC.decode(buffer)) }
            )
    }
}

data class SoulStoneTooltipResponsePayload(
    val owner: UUID,
    val mru: Int?
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<SoulStoneTooltipResponsePayload>("soul_stone_tooltip_response".ecRL)

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, SoulStoneTooltipResponsePayload> =
            StreamCodec.of(
                { buffer, value ->
                    UUIDUtil.STREAM_CODEC.encode(buffer, value.owner)
                    buffer.writeBoolean(value.mru != null)
                    value.mru?.let(buffer::writeVarInt)
                },
                { buffer ->
                    SoulStoneTooltipResponsePayload(
                        UUIDUtil.STREAM_CODEC.decode(buffer),
                        if (buffer.readBoolean()) buffer.readVarInt() else null
                    )
                }
            )
    }
}

object SoulStoneTooltipNetwork {
    private const val CACHE_TTL_MS = 1000L
    private const val REQUEST_THROTTLE_MS = 250L
    private const val MAX_CACHE_SIZE = 128

    private val cache = LinkedHashMap<UUID, CachedMatrix>()
    private val pendingRequests = mutableMapOf<UUID, Long>()

    @JvmStatic
    fun tooltipMru(owner: UUID): Int? {
        val now = System.currentTimeMillis()
        val cached = cache[owner]
        if (cached == null || now - cached.updatedAt > CACHE_TTL_MS) request(owner, now)
        return cached?.mru
    }

    @JvmStatic
    fun acceptResponse(payload: SoulStoneTooltipResponsePayload) {
        pendingRequests.remove(payload.owner)
        cache[payload.owner] = CachedMatrix(payload.mru, System.currentTimeMillis())
        trimCache()
    }

    @JvmStatic
    fun handleRequest(
        requester: ServerPlayer,
        payload: SoulStoneTooltipRequestPayload
    ) {
        val owner =
            requester
                .level()
                .server.playerList
                .getPlayer(payload.owner)
        val mru = owner?.playerMatrix?.mru
        Network.sendTo(requester, SoulStoneTooltipResponsePayload(payload.owner, mru))
    }

    @JvmStatic
    fun clear() {
        cache.clear()
        pendingRequests.clear()
    }

    private fun request(
        owner: UUID,
        now: Long
    ) {
        val lastRequestAt = pendingRequests[owner]
        if (lastRequestAt != null && now - lastRequestAt < REQUEST_THROTTLE_MS) return

        pendingRequests[owner] = now
        SoulStoneTooltipRequestPayload(owner).sendToServer()
    }

    private fun trimCache() {
        while (cache.size > MAX_CACHE_SIZE) {
            val oldest = cache.entries.iterator()
            if (!oldest.hasNext()) return
            oldest.next()
            oldest.remove()
        }
    }

    private data class CachedMatrix(
        val mru: Int?,
        val updatedAt: Long
    )
}
