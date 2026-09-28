package com.algorithmlx.ecr.fabric.init.registry.creative

import com.algorithmlx.ecr.api.registries.CreativeTabOrdering
import com.algorithmlx.ecr.fabric.mixin.CreativeModeTabAccessor
import net.fabricmc.fabric.impl.creativetab.FabricCreativeModeTabImpl
import net.minecraft.core.Holder
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.world.item.CreativeModeTab
import java.util.*

object FabricCreativeTabOrdering {
    private const val TABS_PER_PAGE = 10
    private val ORDERINGS: MutableMap<CreativeModeTab, CreativeTabOrdering> = IdentityHashMap()

    @JvmStatic
    @Synchronized
    fun register(tab: CreativeModeTab, ordering: CreativeTabOrdering) {
        ORDERINGS[tab] = ordering
    }

    @JvmStatic
    fun reorder() {
        val fallback: Comparator<Holder.Reference<CreativeModeTab>> = comparator@{ first, second ->
            val display = first.value().shouldDisplay().compareTo(second.value().shouldDisplay())
            if (display != 0) return@comparator -display
            compare(first.key().identifier(), second.key().identifier())
        }
        val tabs = BuiltInRegistries.CREATIVE_MODE_TAB.listElements()
            .filter { it.key().identifier().namespace != "minecraft" }
            .sorted(fallback)
            .toList()

        val orderings: Map<CreativeModeTab, CreativeTabOrdering>
        synchronized(FabricCreativeTabOrdering::class.java) {
            orderings = IdentityHashMap(ORDERINGS)
        }

        val byId = LinkedHashMap<Identifier, Holder.Reference<CreativeModeTab>>()
        val outgoing = LinkedHashMap<Identifier, MutableSet<Identifier>>()
        val indegree = HashMap<Identifier, Int>()

        tabs.forEach {
            val id = it.key().identifier()
            byId[id] = it
            outgoing[id] = LinkedHashSet()
            indegree[id] = 0
        }

        tabs.forEach {
            val id = it.key().identifier()
            val ordering = orderings[it.value()] ?: return@forEach
            ordering.tabsBefore.forEach { before -> addEdge(before, id, byId, outgoing, indegree) }
            ordering.tabsAfter.forEach { after -> addEdge(id, after, byId, outgoing, indegree) }
        }

        val ready = PriorityQueue<Identifier> { first, second -> fallback.compare(byId[first], byId[second]) }
        indegree.forEach { (id, degree) -> if (degree == 0) ready += id }
        val sorted: ArrayList<Holder.Reference<CreativeModeTab>> = ArrayList(tabs.size)

        while (ready.isNotEmpty()) {
            val id = ready.remove()
            sorted += byId[id]!!

            outgoing[id]!!.forEach {
                val degree = indegree.compute(it) { _, v -> v!! - 1 }!!
                if (degree == 0) ready += it
            }
        }


        if (sorted.size != tabs.size) {
            val cycle = indegree.entries.stream()
                .filter { it.value > 0 }
                .map { it.key }
                .sorted(FabricCreativeTabOrdering::compare)
                .toList()
            throw IllegalStateException("Creative tab ordering creative a cycle: $cycle")
        }

        for (i in sorted.indices) {
            val tab = sorted[i].value()
            val pageIndex = i % TABS_PER_PAGE
            val row = if (pageIndex < TABS_PER_PAGE / 2) CreativeModeTab.Row.TOP else CreativeModeTab.Row.BOTTOM

            (tab as FabricCreativeModeTabImpl).fabric_setPage(i / TABS_PER_PAGE + 1)
            (tab as CreativeModeTabAccessor).also {
                it.setRow(row)
                it.setColumn(if (row == CreativeModeTab.Row.TOP) pageIndex else pageIndex - TABS_PER_PAGE / 2)
            }
        }
    }

    private fun addEdge(
        source: Identifier,
        target: Identifier,
        tabs: Map<Identifier, Holder.Reference<CreativeModeTab>>,
        outgoing: Map<Identifier, MutableSet<Identifier>>,
        indegree: MutableMap<Identifier, Int>
    ) {
        if (!tabs.containsKey(source) || !tabs.containsKey(target)) return
        if (outgoing[source]?.add(target) == true)
            indegree.compute(target) { _, v -> v!! + 1 }
    }

    private fun compare(first: Identifier, second: Identifier): Int {
        val namespace = first.namespace.compareTo(second.namespace)
        return if (namespace != 0) namespace else first.path.compareTo(second.path)
    }
}