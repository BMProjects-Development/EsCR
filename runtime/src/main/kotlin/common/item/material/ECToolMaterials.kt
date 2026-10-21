package com.algorithmlx.ecr.common.item.material

import com.algorithmlx.ecr.api.utils.ecRL
import net.minecraft.core.registries.Registries
import net.minecraft.tags.BlockTags
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ToolMaterial

enum class ECToolMaterials(
    val material: ToolMaterial
) {
    WEAK(
        ToolMaterial(
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            754,
            1.6F,
            7.5F,
            36,
            TagKey.create(Registries.ITEM, "weak_repair".ecRL)
        )
    ),
    ELEMENTAL(
        ToolMaterial(
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL,
            3568,
            5F,
            15F,
            36,
            TagKey.create(Registries.ITEM, "elemental_repair".ecRL)
        )
    )
}
