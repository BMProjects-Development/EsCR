package com.algorithmlx.ecr.common.inventory

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.CraftingRecipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeType

class CraftingFrameComponent(ingredients: List<ItemStack>, val substituteIngredients: Boolean = false) {
    private val grid = List(GRID_SIZE) { ingredients.getOrNull(it)?.copyWithCount(1) ?: ItemStack.EMPTY }
    val ingredients: List<ItemStack> get() = grid.map(ItemStack::copy)

    fun input(): CraftingInput = CraftingInput.of(3, 3, ingredients)

    fun recipe(level: ServerLevel): RecipeHolder<CraftingRecipe>? {
        val input = input()
        if (input.isEmpty) return null
        return level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, level).orElse(null)
    }

    override fun equals(other: Any?): Boolean = other is CraftingFrameComponent &&
        substituteIngredients == other.substituteIngredients && grid.indices.all { ItemStack.matches(grid[it], other.grid[it]) }

    override fun hashCode(): Int = 31 * grid.fold(1) { hash, stack ->
        31 * hash + 31 * ItemStack.hashItemAndComponents(stack) + stack.count
    } + substituteIngredients.hashCode()

    companion object {
        const val GRID_SIZE = 9
        val EMPTY = CraftingFrameComponent(emptyList())

        private val GRID_CODEC = ItemStack.OPTIONAL_CODEC.listOf().validate { grid ->
            if (grid.size == GRID_SIZE) DataResult.success(grid)
            else DataResult.error { "A crafting frame must contain exactly nine ghost ingredients" }
        }

        @JvmField
        val CODEC: Codec<CraftingFrameComponent> = RecordCodecBuilder.create { instance ->
            instance.group(
                GRID_CODEC.fieldOf("ingredients").forGetter(CraftingFrameComponent::ingredients),
                Codec.BOOL.optionalFieldOf("substitute_ingredients", false)
                    .forGetter(CraftingFrameComponent::substituteIngredients)
            ).apply(instance, ::CraftingFrameComponent)
        }

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, CraftingFrameComponent> = StreamCodec.of(
            { buffer, component ->
                component.grid.forEach { ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, it) }
                buffer.writeBoolean(component.substituteIngredients)
            },
            { buffer ->
                CraftingFrameComponent(
                    List(GRID_SIZE) { ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer) },
                    buffer.readBoolean()
                )
            }
        )
    }
}
