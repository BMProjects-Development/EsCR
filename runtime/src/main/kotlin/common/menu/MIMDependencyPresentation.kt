package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.mim.MIMDependencyNode
import com.algorithmlx.ecr.api.mim.MIMDependencyStatus
import net.minecraft.world.item.ItemStack

internal object MIMDependencyPresentation {
    fun flatten(root: MIMDependencyNode, maximum: Int): List<MIMDependencyEntry> {
        val entries = mutableListOf<MIMDependencyEntry>()
        fun append(nodes: List<MIMDependencyNode>, parent: Int) {
            for (siblings in nodes.groupBy { Key(it.stack) }.values) {
                check(entries.size < maximum) { "MIM dependency preview exceeds its transport budget" }
                val index = entries.size
                entries += MIMDependencyEntry(
                    parent, siblings.first().stack.copyWithCount(1), sum(siblings) { it.amount },
                    sum(siblings) { it.stored }, sum(siblings) { it.crafts.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    siblings.maxBy { priority(it.status) }.status
                )
                append(siblings.flatMap { it.children }, index)
            }
        }
        append(listOf(root), -1)
        return entries
    }

    private fun sum(nodes: List<MIMDependencyNode>, value: (MIMDependencyNode) -> Long): Long =
        nodes.fold(0L) { total, node -> total + value(node).coerceAtMost(Long.MAX_VALUE - total) }

    private fun priority(status: MIMDependencyStatus): Int = when (status) {
        MIMDependencyStatus.STORED -> 0
        MIMDependencyStatus.CRAFTABLE -> 1
        MIMDependencyStatus.CYCLE -> 2
        MIMDependencyStatus.MISSING -> 3
        MIMDependencyStatus.LIMIT -> 4
    }

    private class Key(private val stack: ItemStack) {
        override fun equals(other: Any?): Boolean = other is Key && ItemStack.isSameItemSameComponents(stack, other.stack)
        override fun hashCode(): Int = ItemStack.hashItemAndComponents(stack)
    }
}
