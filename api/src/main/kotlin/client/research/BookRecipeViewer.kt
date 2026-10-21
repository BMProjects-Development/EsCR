package com.algorithmlx.ecr.api.client.research

import net.minecraft.world.item.ItemStack
import java.util.concurrent.CopyOnWriteArrayList

fun interface BookRecipeViewer {
    fun openRecipes(stack: ItemStack): Boolean
}

object BookRecipeViewers {
    private val viewers = CopyOnWriteArrayList<BookRecipeViewer>()

    @JvmStatic
    fun register(viewer: BookRecipeViewer) {
        viewers += viewer
    }

    @JvmStatic
    fun openRecipes(stack: ItemStack): Boolean {
        if (stack.isEmpty) return false
        return viewers.any { viewer -> runCatching { viewer.openRecipes(stack) }.getOrDefault(false) }
    }
}
