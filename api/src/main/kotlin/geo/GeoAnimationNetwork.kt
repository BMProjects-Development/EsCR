package com.algorithmlx.ecr.api.geo

import com.algorithmlx.ecr.api.LOGGER
import com.algorithmlx.ecr.api.utils.ecRL
import net.minecraft.core.BlockPos
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.Level
import com.algorithmlx.ecr.api.network.Network

data class GeoBlockAnimationPayload(
    val controllerPos: BlockPos,
    val animation: String,
    val animationType: AnimationType
): CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<GeoBlockAnimationPayload>("geo_block_animation".ecRL)

        @JvmField
        val STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            GeoBlockAnimationPayload::controllerPos,
            ByteBufCodecs.STRING_UTF8,
            GeoBlockAnimationPayload::animation,
            ANIMATION_TYPE_STREAM_CODEC,
            GeoBlockAnimationPayload::animationType,
            ::GeoBlockAnimationPayload
        )
    }
}

data class GeoEntityAnimationPayload(
    val entityId: Int,
    val animation: String,
    val animationType: AnimationType
): CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<GeoEntityAnimationPayload>("geo_entity_animation".ecRL)

        @JvmField
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            GeoEntityAnimationPayload::entityId,
            ByteBufCodecs.STRING_UTF8,
            GeoEntityAnimationPayload::animation,
            ANIMATION_TYPE_STREAM_CODEC,
            GeoEntityAnimationPayload::animationType,
            ::GeoEntityAnimationPayload
        )
    }
}

data class GeoItemAnimationPayload(
    val entityId: Int,
    val equipmentSlot: Int,
    val animation: String,
    val animationType: AnimationType
): CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<GeoItemAnimationPayload>("geo_item_animation".ecRL)

        @JvmField
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            GeoItemAnimationPayload::entityId,
            ByteBufCodecs.VAR_INT,
            GeoItemAnimationPayload::equipmentSlot,
            ByteBufCodecs.STRING_UTF8,
            GeoItemAnimationPayload::animation,
            ANIMATION_TYPE_STREAM_CODEC,
            GeoItemAnimationPayload::animationType,
            ::GeoItemAnimationPayload
        )
    }
}

data class GeoBlockAnimationStopPayload(
    val controllerPos: BlockPos
): CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<GeoBlockAnimationStopPayload>("geo_block_animation_stop".ecRL)

        @JvmField
        val STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            GeoBlockAnimationStopPayload::controllerPos,
            ::GeoBlockAnimationStopPayload
        )
    }
}

data class GeoEntityAnimationStopPayload(
    val entityId: Int
): CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<GeoEntityAnimationStopPayload>("geo_entity_animation_stop".ecRL)

        @JvmField
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            GeoEntityAnimationStopPayload::entityId,
            ::GeoEntityAnimationStopPayload
        )
    }
}

data class GeoItemAnimationStopPayload(
    val entityId: Int,
    val equipmentSlot: Int
): CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<GeoItemAnimationStopPayload>("geo_item_animation_stop".ecRL)

        @JvmField
        val STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            GeoItemAnimationStopPayload::entityId,
            ByteBufCodecs.VAR_INT,
            GeoItemAnimationStopPayload::equipmentSlot,
            ::GeoItemAnimationStopPayload
        )
    }
}

object GeoAnimationNetwork {
    @JvmField
    var playClientBlockAnimation: (GeoBlockAnimationPayload) -> Boolean = { false }

    @JvmField
    var playClientEntityAnimation: (GeoEntityAnimationPayload) -> Boolean = { false }

    @JvmField
    var playClientItemAnimation: (GeoItemAnimationPayload) -> Boolean = { false }

    @JvmField
    var stopClientBlockAnimation: (GeoBlockAnimationStopPayload) -> Boolean = { false }

    @JvmField
    var stopClientEntityAnimation: (GeoEntityAnimationStopPayload) -> Boolean = { false }

    @JvmField
    var stopClientItemAnimation: (GeoItemAnimationStopPayload) -> Boolean = { false }

    @JvmStatic
    fun play(
        level: Level,
        controllerPos: BlockPos,
        animation: String,
        type: AnimationType = AnimationType.PLAY_ONCE
    ): Boolean {
        if (!validateAnimationId(animation)) return false
        val payload = GeoBlockAnimationPayload(controllerPos.immutable(), animation, type)
        if (level.isClientSide) {
            return playClientBlockAnimation(payload)
        }

        val centerX = controllerPos.x + 0.5
        val centerY = controllerPos.y + 0.5
        val centerZ = controllerPos.z + 0.5
        level.players().asSequence()
            .filterIsInstance<ServerPlayer>()
            .filter { player -> player.distanceToSqr(centerX, centerY, centerZ) <= TRACKING_DISTANCE_SQR }
            .forEach { player -> Network.sendTo(player, payload) }
        return true
    }

    @JvmStatic
    fun play(
        entity: Entity,
        animation: String,
        type: AnimationType = AnimationType.PLAY_ONCE
    ): Boolean {
        if (!validateAnimationId(animation)) return false
        val payload = GeoEntityAnimationPayload(entity.id, animation, type)
        val level = entity.level()
        if (level.isClientSide) {
            return playClientEntityAnimation(payload)
        } else {
            nearbyPlayers(level, entity.x, entity.y, entity.z)
                .forEach { player -> Network.sendTo(player, payload) }
        }
        return true
    }

    @JvmStatic
    fun playItem(
        entity: LivingEntity,
        slot: EquipmentSlot,
        animation: String,
        type: AnimationType = AnimationType.PLAY_ONCE
    ): Boolean {
        if (!validateAnimationId(animation)) return false
        val payload = GeoItemAnimationPayload(entity.id, slot.ordinal, animation, type)
        val level = entity.level()
        if (level.isClientSide) {
            return playClientItemAnimation(payload)
        } else {
            nearbyPlayers(level, entity.x, entity.y, entity.z)
                .forEach { player -> Network.sendTo(player, payload) }
        }
        return true
    }

    @JvmStatic
    fun stop(level: Level, controllerPos: BlockPos): Boolean {
        val payload = GeoBlockAnimationStopPayload(controllerPos.immutable())
        if (level.isClientSide) return stopClientBlockAnimation(payload)

        val centerX = controllerPos.x + 0.5
        val centerY = controllerPos.y + 0.5
        val centerZ = controllerPos.z + 0.5
        nearbyPlayers(level, centerX, centerY, centerZ)
            .forEach { player -> Network.sendTo(player, payload) }
        return true
    }

    @JvmStatic
    fun stop(entity: Entity): Boolean {
        val payload = GeoEntityAnimationStopPayload(entity.id)
        val level = entity.level()
        if (level.isClientSide) return stopClientEntityAnimation(payload)

        nearbyPlayers(level, entity.x, entity.y, entity.z)
            .forEach { player -> Network.sendTo(player, payload) }
        return true
    }

    @JvmStatic
    fun stopItem(entity: LivingEntity, slot: EquipmentSlot): Boolean {
        val payload = GeoItemAnimationStopPayload(entity.id, slot.ordinal)
        val level = entity.level()
        if (level.isClientSide) return stopClientItemAnimation(payload)

        nearbyPlayers(level, entity.x, entity.y, entity.z)
            .forEach { player -> Network.sendTo(player, payload) }
        return true
    }

    private fun validateAnimationId(animation: String): Boolean {
        if (animation.isNotBlank()) return true
        LOGGER.error("Cannot play GEO animation: animation id is blank")
        return false
    }

    private fun nearbyPlayers(level: Level, x: Double, y: Double, z: Double): Sequence<ServerPlayer> =
        level.players().asSequence()
            .filterIsInstance<ServerPlayer>()
            .filter { player -> player.distanceToSqr(x, y, z) <= TRACKING_DISTANCE_SQR }

    private const val TRACKING_DISTANCE = 128.0
    private const val TRACKING_DISTANCE_SQR = TRACKING_DISTANCE * TRACKING_DISTANCE
}

private val ANIMATION_TYPE_STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
    { ordinal -> AnimationType.entries.getOrElse(ordinal) { AnimationType.PLAY_ONCE } },
    AnimationType::ordinal
)
