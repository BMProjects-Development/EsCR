package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.research.BookType
import com.algorithmlx.ecr.common.components.BoundGemComponent
import com.algorithmlx.ecr.common.components.PlayerMatrixComponent
import com.algorithmlx.ecr.common.components.SoulStoneComponent
import net.minecraft.core.component.DataComponentType
import net.minecraft.resources.ResourceKey

interface DataComponentRegistry {
    val soulStone: DataComponentType<SoulStoneComponent>
    val bookType: DataComponentType<ResourceKey<BookType>>
    val boundGem: DataComponentType<BoundGemComponent>

    val playerMatrix: DataComponentType<PlayerMatrixComponent>

    companion object {
        @JvmStatic
        lateinit var instance: DataComponentRegistry
    }
}
