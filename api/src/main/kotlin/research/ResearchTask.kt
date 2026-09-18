package com.algorithmlx.ecr.api.research

import com.algorithmlx.ecr.api.utils.StackHelper
import com.mojang.brigadier.StringReader
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import net.minecraft.commands.arguments.item.ItemParser
import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.TagKey
import net.minecraft.world.item.ItemStack

interface ResearchTask {
    val type: Identifier

    fun progress(player: ServerPlayer): ResearchTaskProgress

    fun consume(player: ServerPlayer) = Unit
}

interface OwnerAwareResearchTask : ResearchTask {
    fun progress(
        player: ServerPlayer,
        owner: Identifier,
    ): ResearchTaskProgress
}

fun ResearchTask.progress(
    player: ServerPlayer,
    owner: Identifier,
): ResearchTaskProgress = if (this is OwnerAwareResearchTask) progress(player, owner) else progress(player)

interface ResearchTaskSerializer<T : ResearchTask> {
    val type: Identifier

    fun decode(json: JsonObject): T

    fun encode(value: T): JsonObject
}

data class ResearchTaskProgress(
    val current: Int,
    val required: Int,
) {
    val complete: Boolean get() = current >= required
}

data class ItemResearchTask(
    val item: String,
    val count: Int,
    val consumeItems: Boolean = false,
    val components: JsonObject = JsonObject(emptyMap()),
) : ResearchTask {
    override val type: Identifier = ResearchIds.ITEM_TASK

    override fun progress(player: ServerPlayer): ResearchTaskProgress {
        val required = createStack(player, 1)

        val current = player.inventoryItems().filter { StackHelper.areStacksEqual(it, required) }.sumOf(ItemStack::getCount)
        return ResearchTaskProgress(current.coerceAtMost(count), count)
    }

    override fun consume(player: ServerPlayer) {
        if (!consumeItems) return
        var remaining = count
        val required = createStack(player, 1)
        for (slot in 0 until player.inventory.containerSize) {
            val stack = player.inventory.getItem(slot)
            if (!StackHelper.areStacksEqual(stack, required)) continue
            val removed = remaining.coerceAtMost(stack.count)
            stack.shrink(removed)
            remaining -= removed
            if (remaining == 0) break
        }
    }

    fun createStack(
        player: ServerPlayer,
        stackCount: Int = count,
    ): ItemStack = createStack(player.registryAccess(), stackCount)

    fun createStack(
        provider: HolderLookup.Provider,
        stackCount: Int = count,
    ): ItemStack {
        val amount = stackCount.coerceAtLeast(1)
        val stack = ItemParser(provider).parse(StringReader(item)).createItemStack(amount)
        if (components.isEmpty()) return stack

        val itemId = BuiltInRegistries.ITEM.getKey(stack.item)
        val componentStack =
            ItemParser(provider)
                .parse(StringReader(componentItemDefinition(itemId.toString())))
                .createItemStack(amount)
        stack.applyComponentsAndValidate(componentStack.componentsPatch)
        return stack
    }

    private fun componentItemDefinition(itemId: String): String =
        buildString {
            append(itemId)
            append('[')
            components.entries.forEachIndexed { index, (component, value) ->
                if (index > 0) append(',')
                if (value is JsonNull) {
                    append('!')
                    append(component)
                } else {
                    append(component)
                    append('=')
                    append(value)
                }
            }
            append(']')
        }
}

data class CraftingResearchTask(
    val recipe: Identifier,
) : ResearchTask {
    override val type: Identifier = ResearchIds.RECIPE_TASK

    override fun progress(player: ServerPlayer): ResearchTaskProgress {
        val key = ResourceKey.create(Registries.RECIPE, recipe)
        return ResearchTaskProgress(if (player.recipeBook.contains(key)) 1 else 0, 1)
    }
}

data class OpenResearchTask(
    val research: Identifier? = null,
) : OwnerAwareResearchTask {
    override val type: Identifier = ResearchIds.OPEN_TASK

    override fun progress(player: ServerPlayer): ResearchTaskProgress = ResearchTaskProgress(0, 1)

    override fun progress(
        player: ServerPlayer,
        owner: Identifier,
    ): ResearchTaskProgress {
        val target = research ?: owner
        val data = ResearchProgress.data(player)
        return ResearchTaskProgress(if (target in data.opened || target in data.unlocked) 1 else 0, 1)
    }
}

data class ExperienceResearchTask(
    val amount: Int,
    val levels: Boolean = false,
    val consumeExperience: Boolean = false,
) : ResearchTask {
    override val type: Identifier = ResearchIds.EXPERIENCE_TASK

    override fun progress(player: ServerPlayer): ResearchTaskProgress {
        val current = if (levels) player.experienceLevel else player.totalExperience
        return ResearchTaskProgress(current.coerceAtMost(amount), amount)
    }

    override fun consume(player: ServerPlayer) {
        if (!consumeExperience) return
        if (levels) player.giveExperienceLevels(-amount) else player.giveExperiencePoints(-amount)
    }
}

data class TravelToDimensionResearchTask(
    val dimension: Identifier,
) : ResearchTask {
    override val type: Identifier = ResearchIds.TRAVEL_TO_DIMENSION

    override fun progress(player: ServerPlayer): ResearchTaskProgress {
        val isInDimension = player.level().dimension().identifier() == dimension
        return ResearchTaskProgress(if (isInDimension) 1 else 0, 1)
    }
}

data class TravelToStructureResearchTask(
    val structure: Identifier?,
    val tag: Identifier?,
) : ResearchTask {
    init {
        require((structure == null) xor (tag == null)) {
            "Structure id or Structure tag is required."
        }
    }

    override val type: Identifier = ResearchIds.TRAVEL_TO_STRUCTURE

    override fun progress(player: ServerPlayer): ResearchTaskProgress {
        val level = player.level()
        val pos = player.blockPosition()
        val manager = level.structureManager()

        val isInStructure =
            when {
                structure != null -> {
                    val structureKey = ResourceKey.create(Registries.STRUCTURE, structure)
                    manager.getStructureWithPieceAt(pos) { it.`is`(structureKey) }.isValid
                }

                tag != null -> {
                    val tagKey = TagKey.create(Registries.STRUCTURE, tag)
                    manager.getStructureWithPieceAt(pos, tagKey).isValid
                }

                else -> false
            }

        return ResearchTaskProgress(if (isInStructure) 1 else 0, 1)
    }
}

private fun ServerPlayer.inventoryItems(): Sequence<ItemStack> =
    sequence {
        for (slot in 0 until inventory.containerSize) yield(inventory.getItem(slot))
    }
