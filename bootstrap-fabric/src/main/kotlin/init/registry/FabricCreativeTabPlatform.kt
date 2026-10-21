package com.algorithmlx.ecr.fabric.init.registry

import com.algorithmlx.ecr.api.registries.CreativeTabOrdering
import com.algorithmlx.ecr.api.registries.CreativeTabPlatform
import com.algorithmlx.ecr.fabric.init.registry.creative.FabricCreativeTabOrdering
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab
import net.minecraft.world.item.CreativeModeTab

object FabricCreativeTabPlatform : CreativeTabPlatform {
    override fun createBuilder(): CreativeModeTab.Builder = FabricCreativeModeTab.builder()

    override fun build(builder: CreativeModeTab.Builder, ordering: CreativeTabOrdering): CreativeModeTab =
        builder.build().also { FabricCreativeTabOrdering.register(it, ordering) }
}
