package com.algorithmlx.ecr.api.alloy

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents

@JvmRecord
data class AlloyContents(val material: Identifier, val result: ItemContainerContents) {
    fun output(): ItemStack = result.copyOne()

    companion object {
        @JvmField val CODEC: Codec<AlloyContents> = RecordCodecBuilder.create { instance ->
            instance.group(
                Identifier.CODEC.fieldOf("material").forGetter(AlloyContents::material),
                ItemContainerContents.CODEC.fieldOf("result").forGetter(AlloyContents::result)
            ).apply(instance, ::AlloyContents)
        }
        @JvmField val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, AlloyContents> = StreamCodec.composite(
            Identifier.STREAM_CODEC, AlloyContents::material, ItemContainerContents.STREAM_CODEC, AlloyContents::result, ::AlloyContents
        )
    }
}
