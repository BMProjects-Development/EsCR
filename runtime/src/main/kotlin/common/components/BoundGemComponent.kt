package com.algorithmlx.ecr.common.components

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import java.util.Optional

@JvmRecord
data class BoundGemComponent(
    val pos: BlockPos,
    val dimension: Optional<ResourceKey<Level>> = Optional.empty(),
    val crossDimension: Boolean = false,
    val outsideBoundRadius: Boolean = false
) {
    companion object {
        @JvmField
        val CODEC: Codec<BoundGemComponent> = RecordCodecBuilder.create {
            it.group(
                BlockPos.CODEC.fieldOf("block_pos").forGetter(BoundGemComponent::pos),
                ResourceKey.codec(Registries.DIMENSION).optionalFieldOf("dimension").forGetter(BoundGemComponent::dimension),
                Codec.BOOL.fieldOf("cross_dimension").orElseGet { false }.forGetter(BoundGemComponent::crossDimension),
                Codec.BOOL.optionalFieldOf("outside_bound_radius", false).forGetter(BoundGemComponent::outsideBoundRadius)
            ).apply(it, ::BoundGemComponent)
        }

        @JvmField
        val STREAM_CODEC = StreamCodec.of(::encode, ::decode)

        private fun encode(buf: RegistryFriendlyByteBuf, data: BoundGemComponent) {
            BlockPos.STREAM_CODEC.encode(buf, data.pos)
            buf.writeOptional(data.dimension) { b, resK ->
                ResourceKey.streamCodec(Registries.DIMENSION).encode(b, resK)
            }
            ByteBufCodecs.BOOL.encode(buf, data.crossDimension)
            ByteBufCodecs.BOOL.encode(buf, data.outsideBoundRadius)
        }

        private fun decode(buf: RegistryFriendlyByteBuf): BoundGemComponent {
            val pos = BlockPos.STREAM_CODEC.decode(buf)
            val dimension = buf.readOptional { ResourceKey.streamCodec(Registries.DIMENSION).decode(it) }
            val cross = ByteBufCodecs.BOOL.decode(buf)
            val outsideBoundRadius = ByteBufCodecs.BOOL.decode(buf)
            return BoundGemComponent(pos, dimension, cross, outsideBoundRadius)
        }
    }
}
