package com.algorithmlx.ecr.common.machine

import com.algorithmlx.ecr.api.alloy.AlloyContents
import com.algorithmlx.ecr.api.alloy.AlloyItemFactory
import com.algorithmlx.ecr.registry.DataComponentRegistry
import com.algorithmlx.ecr.registry.ItemRegistry
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.ItemLike

object MagicalAlloyItems: AlloyItemFactory {
    override fun create(contents: AlloyContents, count: Int, name: Component?, item: ItemLike?): ItemStack {
        val stack = ItemStack(item ?: ItemRegistry.magicalAlloy.get(), count)
        require(count in 1..stack.maxStackSize) { "Invalid magical alloy stack size: $count" }
        val result = contents.output()
        require(!result.isEmpty && result.count <= result.maxStackSize) { "Invalid smelting result for alloy ${contents.material}" }
        stack[DataComponentRegistry.alloyContents.get()] = contents
        if (name != null) stack[DataComponents.ITEM_NAME] = name.copy()
        return stack
    }
}
