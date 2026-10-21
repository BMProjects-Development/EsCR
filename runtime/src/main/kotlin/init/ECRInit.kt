package com.algorithmlx.ecr.init

import com.algorithmlx.ecr.api.init.MultiblockMatcherTypes
import com.algorithmlx.ecr.registry.AttachmentRegistry
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.BlockRegistry
import com.algorithmlx.ecr.registry.BookTypeRegistry
import com.algorithmlx.ecr.registry.CreativeTabRegistry
import com.algorithmlx.ecr.registry.DataComponentRegistry
import com.algorithmlx.ecr.registry.ItemRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import com.algorithmlx.ecr.registry.MenuTypeRegistry
import com.algorithmlx.ecr.registry.MobEffectRegistry
import com.algorithmlx.ecr.registry.MultiblockRegistry
import com.algorithmlx.ecr.registry.RecipeDisplayTypeRegistry
import com.algorithmlx.ecr.registry.RecipeSerializerRegistry
import com.algorithmlx.ecr.registry.RecipeTypeRegistry
import com.algorithmlx.ecr.registry.ResearchSerializerRegistry

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
