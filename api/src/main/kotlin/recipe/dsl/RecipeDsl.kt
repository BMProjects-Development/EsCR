package com.algorithmlx.ecr.api.recipe.dsl

import net.minecraft.core.HolderLookup
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.tags.TagKey
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.CookingBookCategory
import net.minecraft.world.item.crafting.CraftingBookCategory
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.ShapedRecipePattern
import net.minecraft.world.level.ItemLike

@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class RecipeDsl

// ingredients
sealed interface IngredientSpec {
    fun resolve(registries: HolderLookup.Provider): Ingredient
}

data class ItemIngredientSpec(val item: ItemLike): IngredientSpec {
    override fun resolve(registries: HolderLookup.Provider) = Ingredient.of(item)
}

data class TagIngredientSpec(val tag: TagKey<Item>): IngredientSpec {
    override fun resolve(registries: HolderLookup.Provider): Ingredient = Ingredient.of(
        registries.lookupOrThrow(Registries.ITEM)
            .getOrThrow(tag)
    )
}

data class NativeIngredientSpec(val ingredient: Ingredient): IngredientSpec {
    override fun resolve(registries: HolderLookup.Provider): Ingredient = ingredient
}

fun ingredient(itemLike: ItemLike): IngredientSpec = ItemIngredientSpec(itemLike)

fun ingredient(tag: TagKey<Item>): IngredientSpec = TagIngredientSpec(tag)

fun ingredient(ingredient: Ingredient): IngredientSpec = NativeIngredientSpec(ingredient)

// result
@JvmInline
value class RecipeResult(val template: ItemStackTemplate)

operator fun ItemLike.times(count: Int): RecipeResult {
    require(count > 0) { "Recipe result must be positive." }
    return RecipeResult(ItemStackTemplate(asItem(), count))
}

fun ItemLike.asRecipeResult(count: Int = 1): RecipeResult = this * count

fun ItemStackTemplate.asRecipeResult(): RecipeResult = RecipeResult(this)

// recipe
fun holder(id: Identifier, recipe: Recipe<*>): RecipeHolder<*> = RecipeHolder(
    ResourceKey.create(Registries.RECIPE, id),
    recipe
)

sealed interface RecipeSpec {
    val id: Identifier

    fun bake(registries: HolderLookup.Provider): RecipeHolder<*>
}

data class AbstractRecipeSpec(
    override val id: Identifier,
    private val factory: (HolderLookup.Provider) -> Recipe<*>
) : RecipeSpec {
    override fun bake(registries: HolderLookup.Provider): RecipeHolder<*> = holder(id, factory(registries))
}

@RecipeDsl
abstract class RecipeScope {
    abstract val id: Identifier

    var showNotification: Boolean = true
    protected var output: RecipeResult? = null
    var result: RecipeResult
        get() = checkNotNull(output) { "Recipe result has not been implemented yet." }
        set(value) { output = value }

    fun result(item: ItemLike, count: Int = 1) {
        result = item.asRecipeResult(count)
    }

    fun result(template: ItemStackTemplate) {
        result = template.asRecipeResult()
    }

    internal fun requireResult(id: Identifier): RecipeResult = requireNotNull(output) { "Recipe $id has no result." }
}

abstract class CraftingRecipeScope: RecipeScope() {
    var category: CraftingBookCategory = CraftingBookCategory.MISC
    var group: String = ""
}

@RecipeDsl
class ShapedRecipeScope(override val id: Identifier): CraftingRecipeScope() {
    private val rows = mutableListOf<String>()
    private val keys = linkedMapOf<Char, IngredientSpec>()

    operator fun String.unaryPlus() {
        rows += this
    }

    fun pattern(vararg rows: String) {
        this.rows += rows
    }

    infix fun Char.means(item: ItemLike) = define(this, ingredient(item))
    infix fun Char.means(tag: TagKey<Item>) = define(this, ingredient(tag))
    infix fun Char.means(ingredient: Ingredient) = define(this, ingredient(ingredient))
    infix fun Char.means(ingredient: IngredientSpec) = define(this, ingredient)

    fun key(char: Char, item: ItemLike) = define(char, ingredient(item))
    fun key(char: Char, tag: TagKey<Item>) = define(char, ingredient(tag))
    fun key(char: Char, ingredient: Ingredient) = define(char, ingredient(ingredient))
    fun key(char: Char, ingredient: IngredientSpec) = define(char, ingredient)

    private fun define(char: Char, ingredient: IngredientSpec) {
        require(char != ShapedRecipePattern.EMPTY_SLOT) { "Space defined as empty slot by default." }
        require(char !in keys) { "$char already defined." }
        keys[char] = ingredient
    }

    internal fun build(): RecipeSpec {
        require(rows.isNotEmpty()) { "Recipe $id has no pattern." }
        require(rows.size <= 3) { "Recipe $id has ${rows.size} rows. Max: 3." }

        val width = rows.first().length

        require(width in 1..3) { "Recipe $id pattern width must be between 1 and 3, actual: $width." }
        require(rows.all { it.length == width }) { "Every pattern row in recipe $id must have the same width." }

        val usedSymbols = rows.asSequence()
            .flatMap { it.asSequence() }
            .filter { it != ShapedRecipePattern.EMPTY_SLOT }
            .toSet()

        val undefined = usedSymbols - keys.keys
        val unused = keys.keys - usedSymbols

        require(undefined.isEmpty()) { "Recipe $id has undefined symbols: $undefined." }
        require(unused.isEmpty()) { "Recipe $id has unused symbols: $unused." }

        return ShapedRecipeSpec(id, rows.toList(), keys.toMap(), result, category, group, showNotification)
    }
}

@RecipeDsl
class ShapelessRecipeScope(override val id: Identifier) : CraftingRecipeScope() {
    private val ingredients = mutableListOf<IngredientSpec>()

    operator fun ItemLike.unaryPlus() = add(this)
    operator fun TagKey<Item>.unaryPlus() = add(this)
    operator fun Ingredient.unaryPlus() = add(this)
    operator fun IngredientSpec.unaryPlus() = add(this)

    fun add(item: ItemLike, count: Int = 1) = add(ingredient(item), count)
    fun add(tag: TagKey<Item>, count: Int = 1) = add(ingredient(tag), count)
    fun add(native: Ingredient, count: Int = 1) = add(ingredient(native), count)
    fun add(ingredient: IngredientSpec, count: Int = 1) {
        require(count > 0) { "Ingredient count must be positive, got $count." }
        repeat(count) { ingredients += ingredient }
    }

    infix fun ItemLike.repeat(count: Int) = add(this, count)
    infix fun TagKey<Item>.repeat(count: Int) = add(this, count)
    infix fun Ingredient.repeat(count: Int) = add(this, count)
    infix fun IngredientSpec.repeat(count: Int) = add(this, count)

    internal fun build(): RecipeSpec {
        require(ingredients.isNotEmpty()) { "Recipe $id has no ingredients." }
        require(ingredients.size <= 9) { "Recipe $id has ${ingredients.size} ingredients; shapeless crafting supports at most 9." }

        return ShapelessRecipeSpec(id, ingredients.toList(), requireResult(id), category, group, showNotification)
    }
}

@RecipeDsl
class CookingRecipeScope(
    override val id: Identifier,
    private val kind: CookingRecipeSpec.Kind,
    defaultCookingTime: Int
) : RecipeScope() {
    private var inputSpec: IngredientSpec? = null
    var experience: Float = 0f
    var cookingTime: Int = defaultCookingTime
    var category: CookingBookCategory = CookingBookCategory.MISC

    var group: String = ""
    var input: IngredientSpec
        get() = checkNotNull(inputSpec) { "Recipe input has not been configured yet" }
        set(value) {
            inputSpec = value
        }

    fun input(item: ItemLike) {
        input = ingredient(item)
    }

    fun input(tag: TagKey<Item>) {
        input = ingredient(tag)
    }

    fun input(native: Ingredient) {
        input = ingredient(native)
    }

    internal fun build(): RecipeSpec {
        require(cookingTime > 0) { "Recipe $id cookingTime must be positive" }
        require(experience >= 0f) { "Recipe $id experience cannot be negative" }

        return CookingRecipeSpec(
            id,
            kind,
            requireNotNull(inputSpec) { "Recipe $id has no input" },
            requireResult(id),
            experience,
            cookingTime,
            category,
            group,
            showNotification
        )
    }
}

@RecipeDsl
class StonecuttingRecipeScope(
    override val id: Identifier
) : RecipeScope() {
    private var inputSpec: IngredientSpec? = null

    var input: IngredientSpec
        get() = checkNotNull(inputSpec) { "Recipe input has not been configured yet" }
        set(value) {
            inputSpec = value
        }

    fun input(item: ItemLike) {
        input = ingredient(item)
    }


    fun input(tag: TagKey<Item>) {
        input = ingredient(tag)
    }

    fun input(native: Ingredient) {
        input = ingredient(native)
    }

    internal fun build(): RecipeSpec = StonecutterRecipeSpec(
        id,
        requireNotNull(inputSpec) { "Recipe $id has no input" },
        requireResult(id),
        showNotification
    )
}

@RecipeDsl
class SmithingTransformRecipeScope(override val id: Identifier) : RecipeScope() {
    var template: IngredientSpec? = null
    var base: IngredientSpec? = null
    var addition: IngredientSpec? = null

    fun template(item: ItemLike) {
        template = ingredient(item)
    }

    fun template(tag: TagKey<Item>) {
        template = ingredient(tag)
    }

    fun template(native: Ingredient) {
        template = ingredient(native)
    }

    fun base(item: ItemLike) {
        base = ingredient(item)
    }

    fun base(tag: TagKey<Item>) {
        base = ingredient(tag)
    }

    fun base(native: Ingredient) {
        base = ingredient(native)
    }

    fun addition(item: ItemLike) {
        addition = ingredient(item)
    }

    fun addition(tag: TagKey<Item>) {
        addition = ingredient(tag)
    }

    fun addition(native: Ingredient) {
        addition = ingredient(native)
    }

    internal fun build(): RecipeSpec = SmithingTransformRecipeSpec(
        id,
        template,
        requireNotNull(base) { "Recipe $id has no smithing base ingredient" },
        addition,
        requireResult(id),
        showNotification
    )
}

@RecipeDsl
class RecipesScope(private val namespace: String) {
    internal val declarations = mutableListOf<RecipeSpec>()

    private fun id(path: String): Identifier {
        require(path.isNotBlank()) { "Recipe path cannot be blank" }

        return if (':' in path) Identifier.parse(path)
        else Identifier.fromNamespaceAndPath(
            namespace,
            path
        )
    }

    infix fun String.shaped(block: ShapedRecipeScope.() -> Unit) {
        val recipeId = id(this)
        declarations += ShapedRecipeScope(recipeId)
            .apply(block)
            .build()
    }

    infix fun String.shapeless(block: ShapelessRecipeScope.() -> Unit) {
        val recipeId = id(this)
        declarations += ShapelessRecipeScope(recipeId)
            .apply(block)
            .build()
    }

    infix fun String.smelting(block: CookingRecipeScope.() -> Unit) = cooking(
        this,
        CookingRecipeSpec.Kind.SMELTING,
        200,
        block
    )

    infix fun String.blasting(block: CookingRecipeScope.() -> Unit) = cooking(
        this,
        CookingRecipeSpec.Kind.BLASTING,
        100,
        block
    )

    infix fun String.smoking(block: CookingRecipeScope.() -> Unit) = cooking(
        this,
        CookingRecipeSpec.Kind.SMOKING,
        100,
        block
    )

    infix fun String.campfire(block: CookingRecipeScope.() -> Unit) = cooking(
        this,
        CookingRecipeSpec.Kind.CAMPFIRE,
        600,
        block
    )

    private fun cooking(path: String, kind: CookingRecipeSpec.Kind, defaultTime: Int, block: CookingRecipeScope.() -> Unit) {
        val recipeId = id(path)
        declarations += CookingRecipeScope(recipeId, kind, defaultTime)
            .apply(block)
            .build()
    }

    infix fun String.stonecutting(block: StonecuttingRecipeScope.() -> Unit ) {
        val recipeId = id(this)
        declarations += StonecuttingRecipeScope(recipeId)
            .apply(block)
            .build()
    }

    infix fun String.smithingTransform(block: SmithingTransformRecipeScope.() -> Unit) {
        val recipeId = id(this)
        declarations += SmithingTransformRecipeScope(recipeId)
            .apply(block)
            .build()
    }

    fun native(path: String, factory: (HolderLookup.Provider) -> Recipe<*>) {
        declarations += AbstractRecipeSpec(id(path), factory)
    }
}
