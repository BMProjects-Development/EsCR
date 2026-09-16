package com.algorithmlx.ecr.common.init

import com.algorithmlx.ecr.api.utils.ecRL
import net.minecraft.core.registries.Registries
import net.minecraft.tags.TagKey

class ECRTags {
    object Blocks {
        @JvmField val ENRICHMENT_CHAMBER = register(ECRModIDs.ENRICHMENT_CHAMBER)

        private fun register(id: String) = TagKey.create(Registries.BLOCK, id.ecRL)
    }

    object Items {
        @JvmField val MRU_LINK_VIEWER_ITEMS = register("link_viewer")

        private fun register(id: String) = TagKey.create(Registries.ITEM, id.ecRL)
    }
}
