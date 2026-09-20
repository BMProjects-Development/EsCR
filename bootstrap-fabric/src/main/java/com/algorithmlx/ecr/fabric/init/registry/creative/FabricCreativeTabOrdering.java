package com.algorithmlx.ecr.fabric.init.registry.creative;

import com.algorithmlx.ecr.api.registries.CreativeTabOrdering;
import com.algorithmlx.ecr.fabric.mixin.CreativeModeTabAccessor;
import net.fabricmc.fabric.impl.creativetab.FabricCreativeModeTabImpl;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

public final class FabricCreativeTabOrdering {
    private static final int TABS_PER_PAGE = 10;
    private static final Map<CreativeModeTab, CreativeTabOrdering> ORDERINGS = new IdentityHashMap<>();
    private static final Set<ResourceKey<CreativeModeTab>> VANILLA_TABS = Set.of(
            CreativeModeTabs.BUILDING_BLOCKS,
            CreativeModeTabs.COLORED_BLOCKS,
            CreativeModeTabs.NATURAL_BLOCKS,
            CreativeModeTabs.FUNCTIONAL_BLOCKS,
            CreativeModeTabs.REDSTONE_BLOCKS,
            CreativeModeTabs.HOTBAR,
            CreativeModeTabs.SEARCH,
            CreativeModeTabs.TOOLS_AND_UTILITIES,
            CreativeModeTabs.COMBAT,
            CreativeModeTabs.FOOD_AND_DRINKS,
            CreativeModeTabs.INGREDIENTS,
            CreativeModeTabs.SPAWN_EGGS,
            CreativeModeTabs.OP_BLOCKS,
            CreativeModeTabs.INVENTORY
    );

    private FabricCreativeTabOrdering() {}

    public static synchronized void register(CreativeModeTab tab, CreativeTabOrdering ordering) {
        ORDERINGS.put(tab, ordering);
    }

    public static void reorder() {
        Comparator<Holder.Reference<CreativeModeTab>> fallback = (first, second) -> {
            int display = Boolean.compare(first.value().shouldDisplay(), second.value().shouldDisplay());
            if (display != 0) return -display;
            return compare(first.key().identifier(), second.key().identifier());
        };
        List<Holder.Reference<CreativeModeTab>> tabs = BuiltInRegistries.CREATIVE_MODE_TAB.listElements()
                .filter(tab -> !VANILLA_TABS.contains(tab.key()))
                .sorted(fallback)
                .toList();
        Map<CreativeModeTab, CreativeTabOrdering> orderings;
        synchronized (FabricCreativeTabOrdering.class) {
            orderings = new IdentityHashMap<>(ORDERINGS);
        }
        Map<Identifier, Holder.Reference<CreativeModeTab>> byId = new LinkedHashMap<>();
        Map<Identifier, Set<Identifier>> outgoing = new LinkedHashMap<>();
        Map<Identifier, Integer> indegree = new HashMap<>();
        for (Holder.Reference<CreativeModeTab> tab : tabs) {
            Identifier id = tab.key().identifier();
            byId.put(id, tab);
            outgoing.put(id, new LinkedHashSet<>());
            indegree.put(id, 0);
        }
        for (Holder.Reference<CreativeModeTab> tab : tabs) {
            Identifier id = tab.key().identifier();
            CreativeTabOrdering ordering = orderings.get(tab.value());
            if (ordering == null) continue;
            for (Identifier before : ordering.getTabsBefore()) addEdge(before, id, byId, outgoing, indegree);
            for (Identifier after : ordering.getTabsAfter()) addEdge(id, after, byId, outgoing, indegree);
        }
        PriorityQueue<Identifier> ready = new PriorityQueue<>((first, second) -> fallback.compare(byId.get(first), byId.get(second)));
        indegree.forEach((id, degree) -> {
            if (degree == 0) ready.add(id);
        });
        List<Holder.Reference<CreativeModeTab>> sorted = new ArrayList<>(tabs.size());
        while (!ready.isEmpty()) {
            Identifier id = ready.remove();
            sorted.add(byId.get(id));
            for (Identifier target : outgoing.get(id)) {
                int degree = indegree.compute(target, (key, value) -> value - 1);
                if (degree == 0) ready.add(target);
            }
        }
        if (sorted.size() != tabs.size()) {
            List<Identifier> cycle = indegree.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .sorted(FabricCreativeTabOrdering::compare)
                    .toList();
            throw new IllegalStateException("Creative tab ordering contains a cycle: " + cycle);
        }
        for (int index = 0; index < sorted.size(); index++) {
            CreativeModeTab tab = sorted.get(index).value();
            int pageIndex = index % TABS_PER_PAGE;
            CreativeModeTab.Row row = pageIndex < TABS_PER_PAGE / 2
                    ? CreativeModeTab.Row.TOP
                    : CreativeModeTab.Row.BOTTOM;
            ((FabricCreativeModeTabImpl) tab).fabric_setPage(index / TABS_PER_PAGE + 1);
            ((CreativeModeTabAccessor) tab).setRow(row);
            ((CreativeModeTabAccessor) tab).setColumn(
                    row == CreativeModeTab.Row.TOP ? pageIndex : pageIndex - TABS_PER_PAGE / 2
            );
        }
    }

    private static void addEdge(
            Identifier source,
            Identifier target,
            Map<Identifier, Holder.Reference<CreativeModeTab>> tabs,
            Map<Identifier, Set<Identifier>> outgoing,
            Map<Identifier, Integer> indegree
    ) {
        if (!tabs.containsKey(source) || !tabs.containsKey(target)) return;
        if (outgoing.get(source).add(target)) indegree.compute(target, (key, value) -> value + 1);
    }

    private static int compare(Identifier first, Identifier second) {
        int namespace = first.getNamespace().compareTo(second.getNamespace());
        return namespace != 0 ? namespace : first.getPath().compareTo(second.getPath());
    }
}
