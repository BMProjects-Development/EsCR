package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.registries.ECRegistryKeys
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.api.research.BookType
import com.algorithmlx.ecr.common.components.BoundGemComponent
import com.algorithmlx.ecr.common.components.PlayerMatrixComponent
import com.algorithmlx.ecr.common.components.SoulStoneComponent
import com.algorithmlx.ecr.init.ECRModIDs
import net.minecraft.core.component.DataComponentType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey

object DataComponentRegistry: RegistrationHandler(ModId) {
    val soulStone = register(ECRModIDs.SOUL_STONE) {
        DataComponentType.builder<SoulStoneComponent>()
            .persistent(SoulStoneComponent.CODEC)
            .networkSynchronized(SoulStoneComponent.STREAM_CODEC)
            .build()
    }

    val bookType = register(ECRModIDs.BOOK_TYPE) {
        DataComponentType.builder<ResourceKey<BookType>>()
            .persistent(ResourceKey.codec(ECRegistryKeys.BOOK_TYPE_KEY))
            .networkSynchronized(ResourceKey.streamCodec(ECRegistryKeys.BOOK_TYPE_KEY))
            .build()
    }

    val boundGem = register(ECRModIDs.BOUND_GEM) {
        DataComponentType.builder<BoundGemComponent>()
            .persistent(BoundGemComponent.CODEC)
            .networkSynchronized(BoundGemComponent.STREAM_CODEC)
            .build()
    }

    val playerMatrix = register(ECRModIDs.PLAYER_MATRIX) {
        DataComponentType.Builder<PlayerMatrixComponent>()
            .persistent(PlayerMatrixComponent.CODEC)
            .networkSynchronized(PlayerMatrixComponent.STREAM_CODEC)
            .build()
    }

    private fun <T : DataComponentType<*>> register(id: String, factory: () -> T) =
        registerNoEntry(id, BuiltInRegistries.DATA_COMPONENT_TYPE, factory)
}
