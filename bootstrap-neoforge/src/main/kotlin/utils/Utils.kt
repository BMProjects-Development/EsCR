package com.algorithmlx.ecr.neoforge.utils

import com.algorithmlx.ecr.api.utils.PlatformUtils
import net.neoforged.fml.ModList

object NeoForgePlatformUtils: PlatformUtils {
    override fun isLoaded(modId: String): Boolean = ModList.get().isLoaded(modId)
}
