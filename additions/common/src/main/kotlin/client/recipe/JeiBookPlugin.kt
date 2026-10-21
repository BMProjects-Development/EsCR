package com.algorithmlx.ecr.additions.client.recipe

import com.algorithmlx.ecr.additions.Additions
import mezz.jei.api.IModPlugin
import mezz.jei.api.JeiPlugin
import mezz.jei.api.runtime.IJeiRuntime
import net.minecraft.resources.Identifier

@JeiPlugin
class JeiBookPlugin : IModPlugin {
    override fun getPluginUid(): Identifier = Identifier.fromNamespaceAndPath(Additions.ModId, "research_book")

    override fun onRuntimeAvailable(jeiRuntime: IJeiRuntime) = JeiBookRecipeViewer.attach(jeiRuntime)

    override fun onRuntimeUnavailable() = JeiBookRecipeViewer.detach()
}
