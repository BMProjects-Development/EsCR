package com.algorithmlx.ecr.common.components

import com.algorithmlx.ecr.api.mru.MRUType
import com.algorithmlx.ecr.api.mru.storage.ModifiableMRUStorage
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

class PlayerMatrixComponent(mru: Int): ModifiableMRUStorage {
    private var mutableMRU: Int = mru.coerceAtLeast(0)
    override val mru: Int get() = mutableMRU
    override val mruCapacity: Int = Int.MAX_VALUE
    override val mruType: MRUType = MRUTypeRegistry.ubmru

    override val isFilled: Boolean = this.mutableMRU == Int.MAX_VALUE

    fun copy(): PlayerMatrixComponent = PlayerMatrixComponent(this.mutableMRU)

    override fun set(amount: Int) {
        this.mutableMRU = amount.coerceAtLeast(0)
    }

    override fun extract(amount: Int): Int {
        if (amount <= 0) return 0
        val extracted = this.mutableMRU.coerceAtMost(amount)
        if (extracted <= 0) return 0

        this.mutableMRU -= extracted

        return extracted
    }

    override fun insert(amount: Int): Int {
        if (amount <= 0) return 0

        val inserted = (Int.MAX_VALUE - this.mutableMRU).coerceAtMost(amount)
        if (inserted <= 0) return 0

        this.mutableMRU += inserted

        return inserted
    }

    companion object {
        @JvmField
        val MAP_CODEC: MapCodec<PlayerMatrixComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.INT.fieldOf("stored_mru").forGetter(PlayerMatrixComponent::mru)
            ).apply(it, ::PlayerMatrixComponent)
        }

        @JvmField
        val CODEC: Codec<PlayerMatrixComponent> = MAP_CODEC.codec()

        @JvmField
        val STREAM_CODEC = StreamCodec.of(::encode, ::decode)

        private fun encode(buf: RegistryFriendlyByteBuf, component: PlayerMatrixComponent) {
            buf.writeInt(component.mutableMRU)
        }

        private fun decode(buf: RegistryFriendlyByteBuf) = PlayerMatrixComponent(buf.readInt())

        fun createEmpty(): PlayerMatrixComponent = PlayerMatrixComponent(0)
    }
}
