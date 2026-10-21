package com.algorithmlx.ecr.api.research

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.research.content.ResearchAction
import com.algorithmlx.ecr.api.research.content.ResearchTargetType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import java.util.concurrent.CopyOnWriteArrayList

interface ResearchRestricted {
    fun requiredResearch(action: ResearchAction): Collection<Identifier>
}

fun interface ResearchRestrictionProvider<T : Any> {
    fun requirements(target: T, action: ResearchAction): Collection<Identifier>
}

object ResearchAccess {
    private data class ProviderEntry<T : Any>(
        val type: Class<T>,
        val provider: ResearchRestrictionProvider<T>
    )

    private val providers = CopyOnWriteArrayList<ProviderEntry<out Any>>()

    @JvmStatic
    fun <T : Any> register(type: Class<T>, provider: ResearchRestrictionProvider<T>) {
        providers += ProviderEntry(type, provider)
    }

    @JvmStatic
    fun canAccess(player: Player, target: Any, action: ResearchAction, notify: Boolean = true): Boolean {
        val requirements = requirements(target, action)
        val missing = requirements.firstOrNull { requirement ->
            if (player is ServerPlayer) !ResearchProgress.has(player, requirement) else !ClientResearchState.has(requirement)
        } ?: return true
        if (notify) player.sendOverlayMessage(Component.translatable("message.$ModId.research_required", missing.toString()))
        return false
    }

    @JvmStatic
    fun requirements(target: Any, action: ResearchAction): Set<Identifier> {
        val requirements = LinkedHashSet<Identifier>()
        if (target is ResearchRestricted) requirements += target.requiredResearch(action)
        normalize(target, action).forEach { normalized ->
            if (normalized !== target && normalized is ResearchRestricted) requirements += normalized.requiredResearch(action)
            providers.forEach { entry -> addFromProvider(entry, normalized, action, requirements) }
            val targetData = targetData(normalized)
            if (targetData != null)
                ResearchCatalog.snapshot().entries.values.forEach { entry ->
                    if (entry.locks.any { it.targetType == targetData.first && it.target == targetData.second && action in it.actions })
                        requirements += entry.id
                }
        }
        return requirements
    }

    @Suppress("UNCHECKED_CAST")
    private fun addFromProvider(
        entry: ProviderEntry<out Any>,
        target: Any,
        action: ResearchAction,
        output: MutableSet<Identifier>
    ) {
        if (entry.type.isInstance(target))
            output += (entry.provider as ResearchRestrictionProvider<Any>).requirements(target, action)
    }

    private fun normalize(target: Any, action: ResearchAction): List<Any> = when (target) {
        is ItemStack -> buildList {
            add(target.item)
            if (action == ResearchAction.PLACE && target.item is BlockItem) add((target.item as BlockItem).block)
        }
        is BlockState -> listOf(target.block)
        is Entity -> listOf(target.type)
        else -> listOf(target)
    }

    private fun targetData(target: Any): Pair<ResearchTargetType, Identifier>? = when (target) {
        is Item -> ResearchTargetType.ITEM to BuiltInRegistries.ITEM.getKey(target)
        is Block -> ResearchTargetType.BLOCK to BuiltInRegistries.BLOCK.getKey(target)
        is EntityType<*> -> ResearchTargetType.ENTITY to BuiltInRegistries.ENTITY_TYPE.getKey(target)
        else -> null
    }
}
