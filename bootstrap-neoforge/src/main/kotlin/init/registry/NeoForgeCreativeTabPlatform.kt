package com.algorithmlx.ecr.neoforge.init.registry

import com.algorithmlx.ecr.api.registries.CreativeTabOrdering
import com.algorithmlx.ecr.api.registries.CreativeTabPlatform
import net.minecraft.world.item.CreativeModeTab

object NeoForgeCreativeTabPlatform : CreativeTabPlatform {
    override fun createBuilder(): CreativeModeTab.Builder = CreativeModeTab.builder()

    override fun build(builder: CreativeModeTab.Builder, ordering: CreativeTabOrdering): CreativeModeTab = builder
        .withTabsBefore(*ordering.tabsBefore.toTypedArray())
        .withTabsAfter(*ordering.tabsAfter.toTypedArray())
        .build()
}
