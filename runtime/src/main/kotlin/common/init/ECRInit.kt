package com.algorithmlx.ecr.common.init

import com.algorithmlx.ecr.api.init.MultiblockMatcherTypes
import com.algorithmlx.ecr.registry.*

object ECRInit {
    fun initRegistries() {
        BookTypeRegistry.init()
        DataComponentRegistry.init()
        MRUTypeRegistry.init()
        BlockRegistry.init()
        ItemRegistry.init()
        BlockEntityTypeRegistry.init()
        MobEffectRegistry.init()
        MenuTypeRegistry.init()
        RecipeTypeRegistry.init()
        RecipeSerializerRegistry.init()
        RecipeDisplayTypeRegistry.init()
        MultiblockMatcherTypes.init()
        MultiblockRegistry.init()
        ResearchSerializerRegistry.init()
        CreativeTabRegistry.init()
        AttachmentRegistry.init()
    }
}
