package com.algorithmlx.ecr.api.multiblock

import com.algorithmlx.ecr.api.registries.ECRegistries
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.world.level.block.state.BlockState

class MultiblockMatcherType<T: MultiblockMatcher>(
    val codec: MapCodec<T>
)

interface MultiblockMatcher {
    val type: MultiblockMatcherType<*>
    val required: Boolean get() = true

    fun matches(block: BlockState): Boolean

    fun default(): BlockState

    companion object {
        @JvmField
        val CODEC: Codec<MultiblockMatcher> =
            ECRegistries.MULTIBLOCK_MATCHER_TYPE.byNameCodec()
                .dispatch(
                    "type",
                    { matcher -> matcher.type },
                    { type -> type.codec }
                )
    }
}
