package com.algorithmlx.ecr.common.components

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import java.util.UUID

@JvmRecord
data class SoulStoneComponent(
    val owner: UUID,
    val ownerName: String,
    val legacyCapacity: Int? = null,
) {
    companion object {
        @JvmField
        val EMPTY = SoulStoneComponent(UUID(0, 0), "")

        @JvmField
        val CODEC: Codec<SoulStoneComponent> = RecordCodecBuilder.create { instance ->
            instance.group(
                UUIDUtil.CODEC.fieldOf("owner").forGetter(SoulStoneComponent::owner),
                Codec.STRING.fieldOf("owner_name").forGetter(SoulStoneComponent::ownerName),
                Codec.INT.optionalFieldOf("capacity", -1)
                    .forGetter { component -> component.legacyCapacity ?: -1 },
            ).apply(instance) { owner, ownerName, capacity ->
                SoulStoneComponent(owner, ownerName, capacity.takeIf { it >= 0 })
            }
        }

        @JvmField
        val STREAM_CODEC: StreamCodec<ByteBuf, SoulStoneComponent> = StreamCodec.of(::encode, ::decode)

        private fun encode(buffer: ByteBuf, component: SoulStoneComponent) {
            UUIDUtil.STREAM_CODEC.encode(buffer, component.owner)
            ByteBufCodecs.STRING_UTF8.encode(buffer, component.ownerName)
            buffer.writeBoolean(component.legacyCapacity != null)
            component.legacyCapacity?.let(buffer::writeInt)
        }

        private fun decode(buffer: ByteBuf): SoulStoneComponent = SoulStoneComponent(
            UUIDUtil.STREAM_CODEC.decode(buffer),
            ByteBufCodecs.STRING_UTF8.decode(buffer),
            if (buffer.readBoolean()) buffer.readInt() else null
        )
    }
}
