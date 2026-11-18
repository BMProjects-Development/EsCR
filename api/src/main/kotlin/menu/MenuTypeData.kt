package com.algorithmlx.ecr.api.menu

import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec

data class MenuTypeData(val pos: BlockPos) {
    companion object {
        val codec: StreamCodec<RegistryFriendlyByteBuf, MenuTypeData> = StreamCodec.composite(BlockPos.STREAM_CODEC, MenuTypeData::pos, ::MenuTypeData)
    }
}
