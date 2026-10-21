package com.algorithmlx.ecr.api.registries

import com.algorithmlx.ecr.api.mru.MRUType
import com.algorithmlx.ecr.api.multiblock.Multiblock
import com.algorithmlx.ecr.api.multiblock.MultiblockMatcherType
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDefinition
import com.algorithmlx.ecr.api.research.BookType
import com.algorithmlx.ecr.api.research.content.BookElementSerializer
import com.algorithmlx.ecr.api.research.ResearchTaskSerializer
import com.mojang.serialization.Lifecycle
import net.minecraft.core.MappedRegistry
import net.minecraft.core.Registry

object ECRegistries {
    @JvmField
    val MRU_TYPE: Registry<MRUType> = MappedRegistry(ECRegistryKeys.MRU_TYPE_KEY, Lifecycle.stable())
    @JvmField
    val MULTIBLOCK: Registry<Multiblock> = MappedRegistry(ECRegistryKeys.MULTIBLOCK_KEY, Lifecycle.stable())
    @JvmField
    val ASSEMBLED_MULTIBLOCK: Registry<AssembledMultiblockDefinition> =
        MappedRegistry(ECRegistryKeys.ASSEMBLED_MULTIBLOCK_KEY, Lifecycle.stable())
    @JvmField
    val BOOK_TYPES: Registry<BookType> = MappedRegistry(ECRegistryKeys.BOOK_TYPE_KEY, Lifecycle.stable())
    @JvmField
    val BOOK_ELEMENT_SERIALIZER: Registry<BookElementSerializer<*>> =
        MappedRegistry(ECRegistryKeys.BOOK_ELEMENT_SERIALIZER_KEY, Lifecycle.stable())
    @JvmField
    val RESEARCH_TASK_SERIALIZER: Registry<ResearchTaskSerializer<*>> =
        MappedRegistry(ECRegistryKeys.RESEARCH_TASK_SERIALIZER_KEY, Lifecycle.stable())
    @JvmField
    val MULTIBLOCK_MATCHER_TYPE: Registry<MultiblockMatcherType<*>> =
        MappedRegistry(ECRegistryKeys.MULTIBLOCK_MATCHER_TYPE_KEY, Lifecycle.stable())
}
