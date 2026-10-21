package com.algorithmlx.ecr.api.init

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.multiblock.BlockMultiblockMatcher
import com.algorithmlx.ecr.api.multiblock.ListMultiblockMatcher
import com.algorithmlx.ecr.api.multiblock.MultiblockMatcherType
import com.algorithmlx.ecr.api.multiblock.TagMultiblockMatcher
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.registries.RegistrationHandler

object MultiblockMatcherTypes : RegistrationHandler(ModId) {
    private val tagReference = registerNoEntry("tag", ECRegistries.MULTIBLOCK_MATCHER_TYPE) {
        MultiblockMatcherType(TagMultiblockMatcher.CODEC)
    }
    private val blockReference = registerNoEntry("block", ECRegistries.MULTIBLOCK_MATCHER_TYPE) {
        MultiblockMatcherType(BlockMultiblockMatcher.CODEC)
    }
    private val listReference = registerNoEntry("list", ECRegistries.MULTIBLOCK_MATCHER_TYPE) {
        MultiblockMatcherType(ListMultiblockMatcher.CODEC)
    }

    val tag get() = tagReference.get()
    val block get() = blockReference.get()
    val list get() = listReference.get()
}
