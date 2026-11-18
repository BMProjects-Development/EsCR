package com.algorithmlx.ecr.api.alloy

import com.algorithmlx.ecr.api.ECRAPIScope
import com.algorithmlx.ecr.api.ECRDsl
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.level.ItemLike

@ECRDsl
class AlloyRegistrationScope(namespace: String): ECRAPIScope(namespace) {
    internal val declarations = mutableListOf<MagicalAlloy>()
    internal val providers = mutableListOf<MagmaticRecipeProviderEntry>()

    fun alloy(path: String, block: AlloyBuilder.() -> Unit): MagicalAlloy = alloy(AlloyBuilder(id(path)).apply(block).build())
    fun alloy(alloy: MagicalAlloy): MagicalAlloy = alloy.also(declarations::add)
    fun provider(path: String, provider: (ServerLevel, ItemStack) -> MagmaticRecipe?) { providers += MagmaticRecipeProviderEntry(id(path), provider) }
}

@ECRDsl
class AlloyBuilder(val id: Identifier) {
    private val inputs = mutableListOf<(ItemStack) -> Boolean>()
    private var result: (() -> ItemStack)? = null
    private var alloyItem: (() -> ItemLike)? = null
    var alloysPerOre: Int = 1
    var name: Component? = null

    fun item(value: ItemLike) = item { value }
    fun item(factory: () -> ItemLike) {
        check(alloyItem == null) { "Alloy $id already has an item" }
        alloyItem = factory
    }

    fun input(item: ItemLike) { inputs += { stack -> stack.`is`(item.asItem()) } }
    fun input(item: () -> ItemLike) { inputs += { stack -> stack.`is`(item().asItem()) } }
    fun input(tag: TagKey<Item>) { inputs += { stack -> stack.`is`(tag) } }
    fun input(ingredient: Ingredient) { inputs += ingredient::test }
    fun matching(predicate: (ItemStack) -> Boolean) { inputs += predicate }

    fun output(item: ItemLike, count: Int = 2) {
        require(count > 0) { "Alloy $id must have a positive smelting result" }
        output { ItemStack(item, count) }
    }
    fun output(stack: ItemStack) {
        require(!stack.isEmpty && stack.count <= stack.maxStackSize) { "Alloy $id has an invalid smelting result" }
        val snapshot = stack.copy()
        output { snapshot.copy() }
    }
    fun output(factory: () -> ItemStack) {
        check(result == null) { "Alloy $id already has a smelting result" }
        result = factory
    }

    fun build(): MagicalAlloy {
        val predicates = inputs.toList()
        val output = requireNotNull(result) { "Alloy $id has no smelting result" }
        return MagicalAlloy(id, { stack -> predicates.any { it(stack.copy()) } }, output, alloysPerOre, name, alloyItem)
    }
}
