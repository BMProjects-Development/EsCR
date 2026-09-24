package com.algorithmlx.ecr.client.book

import com.algorithmlx.ecr.api.research.ResearchNetwork
import com.algorithmlx.ecr.client.book.renderer.BookDefaultRenderers
import com.algorithmlx.ecr.client.screen.ResearchBookScreen
import com.algorithmlx.ecr.common.item.ResearchBookHooks
import net.minecraft.client.Minecraft

object ResearchBookClient {
    @JvmStatic
    fun init() {
        BookDefaultRenderers.init()
        RecipeViewerIntegrations.init()
        ResearchBookHooks.open = { bookType -> Minecraft.getInstance().setScreenAndShow(ResearchBookScreen(bookType)) }
        ResearchNetwork.researchUnlocked = ResearchToast::show
        ResearchNetwork.researchContentUpdated = ResearchToast::showUpdated
    }
}
