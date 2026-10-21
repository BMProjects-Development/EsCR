package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.common.menu.MIMMenuKind
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.NonNullList
import net.minecraft.world.ContainerHelper
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import kotlin.jvm.optionals.getOrNull

object MIMCoreInventoryData {
    private const val ITEMS_TAG = "core_inventory"
    private const val LEGACY_SIZE = 55
    private const val VERSION_TAG = "core_inventory_version"
    private const val VERSION = 1
    private val ENTRY_CODEC: Codec<Entry> = RecordCodecBuilder.create { instance ->
        instance.group(
            Codec.intRange(0, Int.MAX_VALUE - 1).fieldOf("slot").forGetter(Entry::slot),
            ItemStack.CODEC.fieldOf("stack").forGetter(Entry::stack)
        ).apply(instance, ::Entry)
    }

    fun write(output: ValueOutput, items: List<ItemStack>) {
        output.putInt(VERSION_TAG, VERSION)
        output.store(ITEMS_TAG, ENTRY_CODEC.listOf(), items.mapIndexedNotNull { index, stack ->
            if (stack.isEmpty) null else Entry(index, stack)
        })
    }

    fun read(input: ValueInput): NonNullList<ItemStack> {
        val entries = input.read(ITEMS_TAG, ENTRY_CODEC.listOf()).getOrNull()
        val offset = if (isLegacy(input)) 1 else 0
        if (entries == null) {
            val legacy = NonNullList.withSize(LEGACY_SIZE, ItemStack.EMPTY).also { ContainerHelper.loadAllItems(input, it) }
            return NonNullList.withSize(LEGACY_SIZE + offset, ItemStack.EMPTY).also { items ->
                legacy.forEachIndexed { index, stack -> items[index + offset] = stack }
            }
        }
        val size = maxOf(MIMMenuKind.CORE.slots, (entries.maxOfOrNull { it.slot } ?: -1) + 1 + offset)
        return NonNullList.withSize(size, ItemStack.EMPTY).also { items ->
            entries.forEach { items[it.slot + offset] = it.stack }
        }
    }

    fun isLegacy(input: ValueInput): Boolean = input.getIntOr(VERSION_TAG, 0) < VERSION

    private data class Entry(val slot: Int, val stack: ItemStack)
}
