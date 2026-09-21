package com.algorithmlx.ecr.network

import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.mru.resolveMRUDevice
import com.algorithmlx.ecr.api.network.Network
import com.algorithmlx.ecr.api.network.sendToServer
import com.algorithmlx.ecr.api.utils.ecRL
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

data class BoundGemTooltipRequestPayload(
    val pos: BlockPos,
    val dimension: ResourceKey<Level>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<BoundGemTooltipRequestPayload>("bound_gem_tooltip_request".ecRL)

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, BoundGemTooltipRequestPayload> =
            StreamCodec.of(
                { buffer, value ->
                    buffer.writeLong(value.pos.asLong())
                    buffer.writeIdentifier(value.dimension.identifier())
                },
                { buffer ->
                    BoundGemTooltipRequestPayload(
                        BlockPos.of(buffer.readLong()),
                        ResourceKey.create(Registries.DIMENSION, buffer.readIdentifier())
                    )
                }
            )
    }
}

data class BoundGemTooltipResponsePayload(
    val pos: BlockPos,
    val dimension: ResourceKey<Level>,
    val status: BoundGemTargetStatus
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<BoundGemTooltipResponsePayload>("bound_gem_tooltip_response".ecRL)

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, BoundGemTooltipResponsePayload> =
            StreamCodec.of(
                { buffer, value ->
                    buffer.writeLong(value.pos.asLong())
                    buffer.writeIdentifier(value.dimension.identifier())
                    buffer.writeVarInt(value.status.ordinal)
                },
                { buffer ->
                    BoundGemTooltipResponsePayload(
                        BlockPos.of(buffer.readLong()),
                        ResourceKey.create(Registries.DIMENSION, buffer.readIdentifier()),
                        BoundGemTargetStatus.byOrdinal(buffer.readVarInt())
                    )
                }
            )
    }
}

enum class BoundGemTargetStatus {
    UNKNOWN,
    MRU_EXPORTER,
    MRU_CONNECTABLE_NOT_EXPORTER,
    NOT_MRU;

    companion object {
        fun byOrdinal(ordinal: Int): BoundGemTargetStatus = entries.getOrElse(ordinal) { UNKNOWN }
    }
}

object BoundGemTooltipNetwork {
    @JvmField
    var currentDimension: () -> ResourceKey<Level>? = { null }

    private const val CACHE_TTL_MS = 1000L
    private const val REQUEST_THROTTLE_MS = 250L
    private const val MAX_CACHE_SIZE = 128

    private val cache = LinkedHashMap<TargetKey, CachedStatus>()
    private val pendingRequests = mutableMapOf<TargetKey, Long>()

    @JvmStatic
    fun tooltipStatus(
        stack: ItemStack,
        item: BoundGem
    ): BoundGemTargetStatus? {
        val pos = item.getBoundPos(stack) ?: return null
        val dimension = item.getWorld(stack) ?: currentDimension() ?: return null
        val key = TargetKey(pos.immutable(), dimension)
        val now = System.currentTimeMillis()
        val cached = cache[key]

        if (cached == null || now - cached.updatedAt > CACHE_TTL_MS) requestStatus(key, now)

        return cached?.status
    }

    @JvmStatic
    fun acceptResponse(payload: BoundGemTooltipResponsePayload) {
        val key = TargetKey(payload.pos.immutable(), payload.dimension)
        pendingRequests.remove(key)
        cache[key] = CachedStatus(payload.status, System.currentTimeMillis())
        trimCache()
    }

    @JvmStatic
    fun handleRequest(
        player: ServerPlayer,
        payload: BoundGemTooltipRequestPayload
    ) {
        val level = player.level().server.getLevel(payload.dimension)
        val status = level?.let { resolveTargetStatus(it, payload.pos) } ?: BoundGemTargetStatus.UNKNOWN

        Network.sendTo(player, BoundGemTooltipResponsePayload(payload.pos, payload.dimension, status))
    }

    private fun requestStatus(
        key: TargetKey,
        now: Long
    ) {
        val lastRequestAt = pendingRequests[key]
        if (lastRequestAt != null && now - lastRequestAt < REQUEST_THROTTLE_MS) return

        pendingRequests[key] = now
        BoundGemTooltipRequestPayload(key.pos, key.dimension).sendToServer()
    }

    private fun resolveTargetStatus(
        level: ServerLevel,
        pos: BlockPos
    ): BoundGemTargetStatus {
        if (!level.isLoaded(pos)) return BoundGemTargetStatus.UNKNOWN

        val device = level.resolveMRUDevice(pos) ?: return BoundGemTargetStatus.NOT_MRU

        return if (device.deviceType.isExporter) {
            BoundGemTargetStatus.MRU_EXPORTER
        } else if (device.deviceType.isConnectable) {
            BoundGemTargetStatus.MRU_CONNECTABLE_NOT_EXPORTER
        } else {
            BoundGemTargetStatus.NOT_MRU
        }
    }

    private fun trimCache() {
        while (cache.size > MAX_CACHE_SIZE) {
            val oldest = cache.entries.iterator()
            if (!oldest.hasNext()) return
            oldest.next()
            oldest.remove()
        }
    }

    private data class TargetKey(
        val pos: BlockPos,
        val dimension: ResourceKey<Level>
    )

    private data class CachedStatus(
        val status: BoundGemTargetStatus,
        val updatedAt: Long
    )
}
