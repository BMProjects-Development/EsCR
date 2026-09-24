package com.algorithmlx.ecr.common.research

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.research.ResearchCatalog
import com.algorithmlx.ecr.init.config.ECConfig
import net.minecraft.resources.Identifier

object ResearchConfigDisabler {
    private var initialized = false

    @JvmStatic
    fun init() {
        if (initialized) return
        initialized = true
        ResearchCatalog.addDisabledProvider(::configuredResearches)
    }

    @JvmStatic
    fun refresh() {
        ResearchCatalog.refresh()
    }

    private fun configuredResearches(): Collection<Identifier> = ECConfig.current.disabledResearches.mapNotNull(::parseResearch)

    private fun parseResearch(value: String): Identifier? {
        val id = value.trim()
        if (id.isBlank()) return null
        return Identifier.tryParse(if (':' in id) id else "$ModId:$id")
    }
}
