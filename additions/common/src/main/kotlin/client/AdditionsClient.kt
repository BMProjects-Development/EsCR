package com.algorithmlx.ecr.additions.client

import com.algorithmlx.ecr.additions.client.recipe.EmiBookRecipeViewer
import com.algorithmlx.ecr.additions.client.recipe.JeiBookRecipeViewer
import com.algorithmlx.ecr.additions.client.recipe.ReiBookRecipeViewer
import com.algorithmlx.ecr.api.client.research.BookRecipeViewers
import com.algorithmlx.ecr.api.LOGGER
import com.algorithmlx.ecr.api.utils.isLoaded

object AdditionsClient {
    private var initialized = false

    @JvmStatic
    fun init() {
        if (initialized) return
        initialized = true

        if (isLoaded("emi")) BookRecipeViewers.register(EmiBookRecipeViewer)
        if (isLoaded("jei")) BookRecipeViewers.register(JeiBookRecipeViewer)
        if (isLoaded("roughlyenoughitems")) BookRecipeViewers.register(ReiBookRecipeViewer)
        LOGGER.info("Initialized EsCR Oddities client integrations")
    }
}
