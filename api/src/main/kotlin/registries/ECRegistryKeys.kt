package com.algorithmlx.ecr.api.registries

import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.api.mru.MRUType
import com.algorithmlx.ecr.api.multiblock.Multiblock
import com.algorithmlx.ecr.api.multiblock.MultiblockMatcherType
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDefinition
import com.algorithmlx.ecr.api.research.BookType
import com.algorithmlx.ecr.api.research.content.BookElementSerializer
import com.algorithmlx.ecr.api.research.ResearchTaskSerializer
import net.minecraft.core.Registry
import net.minecraft.resources.ResourceKey

object ECRegistryKeys {
    @JvmField
    val MRU_TYPE_KEY: ResourceKey<Registry<MRUType>> = ResourceKey.createRegistryKey("magical_radiation_unit".ecRL)
    @JvmField
    val MULTIBLOCK_KEY: ResourceKey<Registry<Multiblock>> = ResourceKey.createRegistryKey("multiblock".ecRL)
    @JvmField
    val ASSEMBLED_MULTIBLOCK_KEY: ResourceKey<Registry<AssembledMultiblockDefinition>> =
        ResourceKey.createRegistryKey("assembled_multiblock".ecRL)
    @JvmField
    val BOOK_TYPE_KEY: ResourceKey<Registry<BookType>> = ResourceKey.createRegistryKey(("book_type").ecRL)
    @JvmField
    val BOOK_ELEMENT_SERIALIZER_KEY: ResourceKey<Registry<BookElementSerializer<*>>> =
        ResourceKey.createRegistryKey("book_element_serializer".ecRL)
    @JvmField
    val RESEARCH_TASK_SERIALIZER_KEY: ResourceKey<Registry<ResearchTaskSerializer<*>>> =
        ResourceKey.createRegistryKey("research_task_serializer".ecRL)
    @JvmField
    val MULTIBLOCK_MATCHER_TYPE_KEY: ResourceKey<Registry<MultiblockMatcherType<*>>> =
        ResourceKey.createRegistryKey("multiblock_matcher_type".ecRL)
}
