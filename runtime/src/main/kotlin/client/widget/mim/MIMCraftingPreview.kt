package com.algorithmlx.ecr.client.widget.mim

import com.algorithmlx.ecr.api.network.sendToServer
import com.algorithmlx.ecr.common.menu.MIMDependencyRequest
import com.algorithmlx.ecr.common.menu.MIMDependencyResponse
import com.algorithmlx.ecr.common.menu.MIMMenu
import net.minecraft.util.Util
import net.minecraft.world.item.ItemStack

class MIMCraftingSelection(target: ItemStack, val batches: Int) {
    private val template = target.copyWithCount(1)
    val target: ItemStack get() = template.copy()
    fun sameAs(other: MIMCraftingSelection?): Boolean = other != null && batches == other.batches &&
        ItemStack.isSameItemSameComponents(template, other.template)
}

internal class MIMCraftingPreview(
    private val menu: MIMMenu, private val tree: MIMDependencyWindow
) {
    private val queries = mutableListOf<Query>()
    private var treeQuery = 0

    fun openTree(slot: Int, target: ItemStack, batches: Int) {
        tree.loading(slot, target, batches)
        request(MIMCraftingSelection(target, batches), true)
    }

    fun update() {
        val now = Util.getMillis()
        queries.removeIf { now - it.sentAt > EXPIRY_MS }
        if (tree.visible) tree.selection?.let { selected ->
            if (queries.none { it.selection.sameAs(selected) && now - it.sentAt < REFRESH_MS }) request(selected, false)
        }
    }

    fun accept(response: MIMDependencyResponse) {
        if (response.containerId != menu.containerId) return
        val query = queries.firstOrNull { it.id == response.requestId } ?: return
        if (query.received) return
        query.received = true
        if (queries.any { it.id > query.id && it.received && it.selection.sameAs(query.selection) }) return
        if (query.selection.sameAs(tree.selection) && response.requestId >= treeQuery) {
            treeQuery = response.requestId
            tree.accept(response)
        }
    }

    private fun request(selection: MIMCraftingSelection, openTree: Boolean) {
        val id = ++sequence
        if (openTree) treeQuery = id
        queries += Query(id, selection, Util.getMillis())
        MIMDependencyRequest(menu.containerId, id, selection.target, selection.batches).sendToServer()
    }

    private class Query(val id: Int, val selection: MIMCraftingSelection, val sentAt: Long, var received: Boolean = false)

    companion object {
        private const val REFRESH_MS = 500L
        private const val EXPIRY_MS = 5000L
        private var sequence = 0
    }
}
