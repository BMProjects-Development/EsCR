package com.algorithmlx.ecr.api.registries

import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.ItemStack
import java.util.function.Supplier

class CreativeTabBuilder {
    private val platform = CreativeTabPlatform.instance
    private val delegate = platform.createBuilder()
    private val tabsBefore = linkedSetOf<Identifier>()
    private val tabsAfter = linkedSetOf<Identifier>()
    private var built = false

    fun title(title: Component) = apply {
        delegate.title(title)
    }

    fun icon(icon: Supplier<ItemStack>) = apply {
        delegate.icon(icon)
    }

    fun displayItems(generator: CreativeModeTab.DisplayItemsGenerator) = apply {
        delegate.displayItems(generator)
    }

    fun alignedRight() = apply {
        delegate.alignedRight()
    }

    fun hideTitle() = apply {
        delegate.hideTitle()
    }

    fun noScrollBar() = apply {
        delegate.noScrollBar()
    }

    fun backgroundTexture(texture: Identifier) = apply {
        delegate.backgroundTexture(texture)
    }

    fun withTabsBefore(vararg tabs: Identifier) = apply {
        tabs.forEach(::addTabBefore)
    }

    fun withTabsBefore(vararg tabs: ResourceKey<CreativeModeTab>) = withTabsBefore(
        *tabs.map(ResourceKey<CreativeModeTab>::identifier).toTypedArray(),
    )

    fun withTabsAfter(vararg tabs: Identifier) = apply {
        tabs.forEach(::addTabAfter)
    }

    fun withTabsAfter(vararg tabs: ResourceKey<CreativeModeTab>) = withTabsAfter(
        *tabs.map(ResourceKey<CreativeModeTab>::identifier).toTypedArray(),
    )

    fun build(): CreativeModeTab {
        check(!built) { "Creative tab builder has already been built" }
        built = true
        return platform.build(
            delegate,
            CreativeTabOrdering(tabsBefore.toList(), tabsAfter.toList()),
        )
    }

    private fun addTabBefore(tab: Identifier) {
        require(tab !in tabsAfter) { "Creative tab $tab cannot be ordered both before and after this tab" }
        tabsBefore += tab
    }

    private fun addTabAfter(tab: Identifier) {
        require(tab !in tabsBefore) { "Creative tab $tab cannot be ordered both before and after this tab" }
        tabsAfter += tab
    }
}

data class CreativeTabOrdering(
    val tabsBefore: List<Identifier>,
    val tabsAfter: List<Identifier>,
)

interface CreativeTabPlatform {
    fun createBuilder(): CreativeModeTab.Builder

    fun build(builder: CreativeModeTab.Builder, ordering: CreativeTabOrdering): CreativeModeTab

    companion object {
        @JvmStatic
        lateinit var instance: CreativeTabPlatform
    }
}

fun creativeTab(block: CreativeTabBuilder.() -> Unit): CreativeModeTab = CreativeTabBuilder().apply(block).build()
