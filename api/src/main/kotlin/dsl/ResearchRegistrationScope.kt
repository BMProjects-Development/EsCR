package com.algorithmlx.ecr.api.dsl

import com.algorithmlx.ecr.api.ECRAPIScope
import com.algorithmlx.ecr.api.research.ResearchCatalog
import com.algorithmlx.ecr.api.research.ResearchCatalogSnapshot
import com.algorithmlx.ecr.api.research.content.BookCategory
import com.algorithmlx.ecr.api.research.content.BookEntry
import net.minecraft.resources.Identifier

class ResearchRegistrationScope(namespace: String): ECRAPIScope(namespace) {
    fun category(category: BookCategory) = ResearchCatalog.register(category)
    fun entry(entry: BookEntry) = ResearchCatalog.register(entry)
    fun disabled(provider: () -> Collection<Identifier>) = ResearchCatalog.addDisabledProvider(provider)
    fun onReload(listener: (ResearchCatalogSnapshot) -> Unit) = ResearchCatalog.onReload(listener)
}
