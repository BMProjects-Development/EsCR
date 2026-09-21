package com.algorithmlx.ecr.network

import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.api.network.Network
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.max

data class MagicShieldPayload(
    val entityId: Int,
    val hitX: Float,
    val hitY: Float,
    val hitZ: Float,
    val blocked: Boolean
): CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField
        val TYPE = CustomPacketPayload.Type<MagicShieldPayload>("magic_shield".ecRL)

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MagicShieldPayload> = StreamCodec.of(
            { buf, value ->
                buf.writeVarInt(value.entityId)
                buf.writeFloat(value.hitX)
                buf.writeFloat(value.hitY)
                buf.writeFloat(value.hitZ)
                buf.writeBoolean(value.blocked)
            },
            {
                MagicShieldPayload(
                    it.readVarInt(),
                    it.readFloat(), it.readFloat(), it.readFloat(),
                    it.readBoolean()
                )
            }
        )
    }
}

object MagicShieldNetwork {
    fun show(level: ServerLevel, target: Entity, source: DamageSource, blocked: Boolean) {
        val position = hitPosition(target, source)
        val payload = MagicShieldPayload(
            target.id, position.x.toFloat(), position.y.toFloat(), position.z.toFloat(), blocked
        )

        level.players().filter { it.distanceToSqr(target) <= MAX_DIST_SQUARE }.forEach { Network.sendTo(it, payload) }
    }

    private fun hitPosition(target: Entity, source: DamageSource): Vec3 {
        val box = target.boundingBox
        val center = box.center
        val attacker = source.entity ?: return target.lookAngle.scale(-1.0)
        val directAttacker = source.directEntity

        val start: Vec3
        val end: Vec3

        if (directAttacker != null && directAttacker !== attacker) {
            start = Vec3(directAttacker.xo, directAttacker.yo, directAttacker.zo)
            end = directAttacker.position().add(directAttacker.deltaMovement)
        } else {
            start = attacker.eyePosition
            end = start.add(attacker.lookAngle.scale(MAX_DIST))
        }

        val hit = box.clip(start, end).orElseGet {
            Vec3(start.x.coerceIn(box.minX, box.maxX), start.y.coerceIn(box.minY, box.maxY), start.z.coerceIn(box.minZ, box.maxZ))
        }
        val offset = hit.subtract(center)
        val position = Vec3(
            offset.x / max(box.xsize * 0.5, MIN_RADIUS),
            offset.y / max(box.ysize * 0.5, MIN_RADIUS),
            offset.z / max(box.zsize * 0.5, MIN_RADIUS)
        )
        val maxAxis = max(abs(position.x), max(abs(position.y), abs(position.z)))

        if (maxAxis < MAX_DIRECTION_LENGTH) return target.lookAngle.scale(-1.0)

        return position.scale(1.0 / maxAxis)
    }

    private const val MAX_DIST = 128.0
    private const val MAX_DIST_SQUARE = MAX_DIST * MAX_DIST
    private const val MAX_DIRECTION_LENGTH = 0.000001
    private const val MIN_RADIUS = 0.15
}
