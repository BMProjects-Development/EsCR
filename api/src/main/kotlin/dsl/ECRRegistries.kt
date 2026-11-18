package com.algorithmlx.ecr.api.dsl

import com.algorithmlx.ecr.api.ECRDsl
import com.algorithmlx.ecr.api.mru.MRUType
import com.algorithmlx.ecr.api.multiblock.Multiblock
import com.algorithmlx.ecr.api.multiblock.MultiblockMatcherType
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDefinition
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.api.research.BookType
import com.algorithmlx.ecr.api.research.ResearchTaskSerializer
import com.algorithmlx.ecr.api.research.content.BookElementSerializer

@ECRDsl
class ECRRegistries(namespace: String): RegistrationHandler(namespace) {
    fun mruType(path: String, factory: () -> MRUType) = registerNoEntry(path, ECRegistries.MRU_TYPE, factory)
    fun multiblock(path: String, factory: () -> Multiblock) = registerNoEntry(path, ECRegistries.MULTIBLOCK, factory)
    fun assembledMultiblock(path: String, factory: () -> AssembledMultiblockDefinition) = registerNoEntry(path, ECRegistries.ASSEMBLED_MULTIBLOCK, factory)
    fun bookType(path: String, factory: () -> BookType) = registerNoEntry(path, ECRegistries.BOOK_TYPES, factory)
    fun bookElement(path: String, factory: () -> BookElementSerializer<*>) = registerNoEntry(path, ECRegistries.BOOK_ELEMENT_SERIALIZER, factory)
    fun researchTask(path: String, factory: () -> ResearchTaskSerializer<*>) = registerNoEntry(path, ECRegistries.RESEARCH_TASK_SERIALIZER, factory)
    fun multiblockMatcher(path: String, factory: () -> MultiblockMatcherType<*>) = registerNoEntry(path, ECRegistries.MULTIBLOCK_MATCHER_TYPE, factory)
}
