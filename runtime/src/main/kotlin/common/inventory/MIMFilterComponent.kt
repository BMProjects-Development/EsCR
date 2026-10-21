package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.registry.DataComponentRegistry
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.api.mim.MIMFilters
import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.component.DataComponents
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack

class MIMFilterComponent(
    samples: List<ItemStack>,
    val blacklist: Boolean = false,
    val ignoreComponents: Boolean = false,
    val ignoreDamage: Boolean = false,
    val commonTags: Boolean = false
) {
    private val templates = List(SIZE) { samples.getOrNull(it)?.copyWithCount(1) ?: ItemStack.EMPTY }
    val samples: List<ItemStack> get() = templates.map(ItemStack::copy)

    fun matches(stack: ItemStack): Boolean = !stack.isEmpty && evaluate(stack, 0, TraversalBudget()) == true

    fun isValid(depth: Int = 0): Boolean = validate(depth, TraversalBudget())

    private fun validate(depth: Int, budget: TraversalBudget): Boolean {
        if (depth >= budget.maxDepth) return false
        for (sample in templates) {
            if (!budget.visit()) return false
            if (!sample.isEmpty && sample.item is MIMFilterItem &&
                !(sample[DataComponentRegistry.mimFilter.get()] ?: EMPTY).validate(depth + 1, budget))
                return false
        }
        return true
    }

    private fun evaluate(stack: ItemStack, depth: Int, budget: TraversalBudget): Boolean? {
        if (depth >= budget.maxDepth) return null
        var found = false
        for (template in templates) {
            if (!budget.visit()) return null
            if (template.isEmpty) continue
            val nested = template.item as? MIMFilterItem
            val matches = if (nested != null)
                (template[DataComponentRegistry.mimFilter.get()] ?: EMPTY).evaluate(stack, depth + 1, budget) ?: return null
            else if (MIMFilters.provider(template) != null)
                MIMFilters.matches(template, stack)
            else
                matchesTemplate(template, stack)
            found = found || matches
        }
        return if (blacklist) !found else found
    }

    private fun matchesTemplate(template: ItemStack, stack: ItemStack): Boolean {
        if (commonTags && template.typeHolder().tags().anyMatch { stack.`is`(it) }) return true
        if (template.item !== stack.item) return false
        if (ignoreComponents) return true
        if (!ignoreDamage) return ItemStack.isSameItemSameComponents(template, stack)
        val expected = template.copy()
        val actual = stack.copy()
        expected.remove(DataComponents.DAMAGE)
        actual.remove(DataComponents.DAMAGE)
        return ItemStack.isSameItemSameComponents(expected, actual)
    }

    override fun equals(other: Any?): Boolean = other is MIMFilterComponent && blacklist == other.blacklist &&
        ignoreComponents == other.ignoreComponents && ignoreDamage == other.ignoreDamage && commonTags == other.commonTags &&
        templates.indices.all { ItemStack.matches(templates[it], other.templates[it]) }

    override fun hashCode(): Int {
        val samplesHash = templates.fold(1) { hash, stack ->
            31 * hash + if (stack.isEmpty) 0 else 31 * ItemStack.hashItemAndComponents(stack) + stack.count
        }
        return listOf(samplesHash, blacklist, ignoreComponents, ignoreDamage, commonTags).hashCode()
    }

    private class TraversalBudget {
        private val config = ECConfig.current.inventoryManager
        val maxDepth = config.filterMaxDepth
        private var remaining = config.filterMaxNodes

        fun visit(): Boolean {
            if (remaining <= 0) return false
            remaining--
            return true
        }
    }

    companion object {
        const val SIZE = 9
        val EMPTY = MIMFilterComponent(emptyList())
        private val SAMPLES_CODEC = ItemStack.OPTIONAL_CODEC.listOf().validate { samples ->
            if (samples.size == SIZE) DataResult.success(samples)
            else DataResult.error { "An inventory manager filter must contain exactly nine samples" }
        }

        @JvmField val CODEC: Codec<MIMFilterComponent> = RecordCodecBuilder.create { instance ->
            instance.group(
                SAMPLES_CODEC.fieldOf("samples").forGetter(MIMFilterComponent::samples),
                Codec.BOOL.optionalFieldOf("blacklist", false).forGetter(MIMFilterComponent::blacklist),
                Codec.BOOL.optionalFieldOf("ignore_components", false).forGetter(MIMFilterComponent::ignoreComponents),
                Codec.BOOL.optionalFieldOf("ignore_damage", false).forGetter(MIMFilterComponent::ignoreDamage),
                Codec.BOOL.optionalFieldOf("common_tags", false).forGetter(MIMFilterComponent::commonTags)
            ).apply(instance, ::MIMFilterComponent)
        }

        @JvmField val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MIMFilterComponent> = StreamCodec.of(
            { buffer, value ->
                value.templates.forEach { ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, it) }
                buffer.writeBoolean(value.blacklist)
                buffer.writeBoolean(value.ignoreComponents)
                buffer.writeBoolean(value.ignoreDamage)
                buffer.writeBoolean(value.commonTags)
            },
            { buffer ->
                MIMFilterComponent(List(SIZE) { ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer) },
                    buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean())
            }
        )
    }
}
