package com.algorithmlx.ecr.api.alloy

import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.ItemContainerContents
import net.minecraft.world.level.ItemLike

class MagicalAlloy(
    val id: Identifier,
    private val accepts: (ItemStack) -> Boolean,
    private val output: () -> ItemStack,
    val alloysPerOre: Int = 1,
    name: Component? = null,
    private val item: (() -> ItemLike)? = null
) {
    private val displayName = (name ?: Component.translatable("alloy.${id.namespace}.${id.path.replace('/', '.')}")).copy()
    val name: Component get() = displayName.copy()

    init { require(alloysPerOre > 0) { "Alloy $id must produce a positive number of alloys" } }

    fun matches(stack: ItemStack): Boolean = !stack.isEmpty && accepts(stack.copy())
    fun isItem(stack: ItemStack): Boolean = !stack.isEmpty && item?.let { stack.`is`(it().asItem()) } == true

    fun result(): ItemStack = output().copy().also {
        require(!it.isEmpty && it.count <= it.maxStackSize) { "Alloy $id has an invalid smelting result" }
    }

    fun create(count: Int = alloysPerOre): ItemStack {
        require(count > 0) { "Alloy stack size must be positive" }
        val contents = AlloyContents(id, ItemContainerContents.fromItems(listOf(result())))
        return MagicalAlloys.itemFactory.create(contents, count, name, item?.invoke())
    }
}

fun interface AlloyItemFactory {
    fun create(contents: AlloyContents, count: Int, name: Component?, item: ItemLike?): ItemStack
}

object MagicalAlloys {
    private data class State(
        val entries: Map<Identifier, MagicalAlloy> = emptyMap(),
        val providers: Map<Identifier, (ServerLevel, ItemStack) -> MagmaticRecipe?> = emptyMap()
    )

    @Volatile private var state = State()
    @Volatile private var factory: AlloyItemFactory? = null

    var itemFactory: AlloyItemFactory
        get() = checkNotNull(factory) { "Magical alloy items are unavailable before EsCR runtime initialization" }
        set(value) { factory = value }

    @JvmStatic
    fun register(alloy: MagicalAlloy): MagicalAlloy {
        registerAll(listOf(alloy))
        return alloy
    }

    @JvmStatic
    fun registerAll(alloys: Collection<MagicalAlloy>) = registerAll(alloys, emptyList())

    @Synchronized
    internal fun registerAll(alloys: Collection<MagicalAlloy>, providers: Collection<MagmaticRecipeProviderEntry>) {
        val incoming = alloys.toList()
        val recipes = providers.toList()
        val ids = incoming.map(MagicalAlloy::id)
        val recipeIds = recipes.map(MagmaticRecipeProviderEntry::id)
        require(ids.distinct().size == ids.size) { "Duplicate magical alloy ids in registration batch" }
        require(ids.none(state.entries::containsKey)) { "Magical alloys are already registered: ${ids.filter(state.entries::containsKey)}" }
        require(recipeIds.distinct().size == recipeIds.size) { "Duplicate magmatic recipe provider ids in registration batch" }
        require(recipeIds.none(state.providers::containsKey)) { "Magmatic recipe providers are already registered: ${recipeIds.filter(state.providers::containsKey)}" }
        state = State(state.entries + incoming.associateBy(MagicalAlloy::id), state.providers + recipes.associate { it.id to it.provider })
    }

    @JvmStatic
    operator fun get(id: Identifier): MagicalAlloy? = state.entries[id]

    @JvmStatic
    fun values(): List<MagicalAlloy> = state.entries.values.toList()

    @JvmStatic
    fun find(stack: ItemStack): MagicalAlloy? = state.entries.values.firstOrNull { it.matches(stack) }

    @JvmStatic
    fun findItem(stack: ItemStack): MagicalAlloy? = state.entries.values.firstOrNull { it.isItem(stack) }

    internal fun findRecipe(level: ServerLevel, input: ItemStack): MagmaticRecipe? =
        if (input.isEmpty) null else state.providers.values.firstNotNullOfOrNull { it(level, input.copy()) }
}
