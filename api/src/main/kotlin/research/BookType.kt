package com.algorithmlx.ecr.api.research

import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.registries.ECRegistryKeys
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.ResourceKey

@JvmRecord
data class BookType(
    val order: Int,
    val inheritedTypes: Set<ResourceKey<BookType>>
) {
    fun name(): Component {
        val key = ECRegistries.BOOK_TYPES.getKey(this) ?: throw NullPointerException("Book Type is not registered.")
        return Component.translatable("book_type.${key.namespace}.${key.path}")
    }

    companion object {
        val codec: Codec<BookType> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("order").forGetter(BookType::order),
                Codec.list(ResourceKey.codec(ECRegistryKeys.BOOK_TYPE_KEY))
                    .fieldOf("inherited_types").forGetter { it.inheritedTypes.toList() }
            ).apply(instance) { order, levels -> BookType(order, levels.toSet()) }
        }

        val codecStream: StreamCodec<ByteBuf, BookType> = StreamCodec.composite(
            ByteBufCodecs.INT, BookType::order,
            ByteBufCodecs.list<ByteBuf, ResourceKey<BookType>>()
                .apply(ResourceKey.streamCodec(ECRegistryKeys.BOOK_TYPE_KEY)), { it.inheritedTypes.toList() },
            { order, list -> BookType(order, list.toSet()) }
        )
    }
}
