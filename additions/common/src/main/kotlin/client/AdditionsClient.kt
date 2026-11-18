package com.algorithmlx.ecr.additions.client

import com.algorithmlx.ecr.additions.client.recipe.EmiBookRecipeViewer
import com.algorithmlx.ecr.additions.client.recipe.JeiBookRecipeViewer
import com.algorithmlx.ecr.additions.client.recipe.ReiBookRecipeViewer
import com.algorithmlx.ecr.api.escrAPI
import com.algorithmlx.ecr.api.LOGGER
import com.algorithmlx.ecr.api.utils.isLoaded

object AdditionsClient {
    private var initialized = false

    @JvmStatic
    fun init() {
        if (initialized) return
        initialized = true

        escrAPI { client {
            if (isLoaded("emi")) recipeViewer(EmiBookRecipeViewer)
            if (isLoaded("jei")) recipeViewer(JeiBookRecipeViewer)
            if (isLoaded("roughlyenoughitems")) recipeViewer(ReiBookRecipeViewer)
        } }
        LOGGER.info("Initialized EsCR Oddities client integrations")
    }
}
