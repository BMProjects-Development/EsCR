package com.algorithmlx.ecr.common.recipe

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.registry.BlockRegistry
import com.algorithmlx.ecr.registry.RecipeDisplayTypeRegistry
import com.algorithmlx.ecr.registry.RecipeSerializerRegistry
import com.algorithmlx.ecr.registry.RecipeTypeRegistry
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.*
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay
import net.minecraft.world.level.Level

class MithrilineFurnaceRecipe(
    val input: Ingredient,
    val espe: Int,
    private val result: ItemStackTemplate
) : Recipe<SingleRecipeInput> {
    override fun matches(
        input: SingleRecipeInput,
        level: Level
    ): Boolean {
        if (level.isClientSide || input.isEmpty) return false

        return this.input.test(input.item())
    }

    override fun assemble(input: SingleRecipeInput): ItemStack = this.result.create()

    override fun showNotification(): Boolean = false

    override fun group(): String = "$ModId:${ECRModIDs.MITHRILINE_FURNACE}"

    override fun getSerializer(): RecipeSerializer<out Recipe<SingleRecipeInput>> = RecipeSerializerRegistry.mithrilineFurnace

    override fun getType(): RecipeType<out Recipe<SingleRecipeInput>> = RecipeTypeRegistry.mithrilineFurnace.get()

    override fun placementInfo(): PlacementInfo = PlacementInfo.NOT_PLACEABLE

    override fun recipeBookCategory(): RecipeBookCategory = RecipeBookCategories.FURNACE_MISC

    override fun display(): List<RecipeDisplay> =
        listOf(
            Display(
                input.display(),
                SlotDisplay.ItemStackSlotDisplay(result),
                SlotDisplay.ItemSlotDisplay(BlockRegistry.mithrilineFurnace.get().asItem())
            )
        )

    @JvmRecord
    data class Display(
        val ingredient: SlotDisplay,
        private val resultDisplay: SlotDisplay,
        private val station: SlotDisplay
    ) : RecipeDisplay {
        override fun result(): SlotDisplay = resultDisplay

        override fun craftingStation(): SlotDisplay = station

        override fun type(): RecipeDisplay.Type<out RecipeDisplay> = RecipeDisplayTypeRegistry.mithrilineFurnace

        companion object {
            @JvmField
            val MAP_CODEC: MapCodec<Display> =
                RecordCodecBuilder.mapCodec {
                    it
                        .group(
                            SlotDisplay.CODEC.fieldOf("input").forGetter(Display::ingredient),
                            SlotDisplay.CODEC.fieldOf("result").forGetter(Display::resultDisplay),
                            SlotDisplay.CODEC.fieldOf("station").forGetter(Display::station)
                        ).apply(it, ::Display)
                }

            @JvmField
            val STREAM_CODEC =
                StreamCodec.composite(
                    SlotDisplay.STREAM_CODEC,
                    Display::ingredient,
                    SlotDisplay.STREAM_CODEC,
                    Display::resultDisplay,
                    SlotDisplay.STREAM_CODEC,
                    Display::station,
                    ::Display
                )
        }
    }

    companion object {
        @JvmField
        val CODEC: MapCodec<MithrilineFurnaceRecipe> =
            RecordCodecBuilder.mapCodec {
                it
                    .group(
                        Ingredient.CODEC.fieldOf("input").forGetter(MithrilineFurnaceRecipe::input),
                        Codec.INT.fieldOf("espe").forGetter(MithrilineFurnaceRecipe::espe),
                        ItemStackTemplate.CODEC.fieldOf("result").forGetter(MithrilineFurnaceRecipe::result)
                    ).apply(it, ::MithrilineFurnaceRecipe)
            }

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MithrilineFurnaceRecipe> =
            StreamCodec.of(
                ::encode,
                ::decode
            )

        private fun encode(
            buf: RegistryFriendlyByteBuf,
            recipe: MithrilineFurnaceRecipe
        ) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.input)
            buf.writeInt(recipe.espe)
            ItemStackTemplate.STREAM_CODEC.encode(buf, recipe.result)
        }

        private fun decode(buf: RegistryFriendlyByteBuf): MithrilineFurnaceRecipe {
            val input = Ingredient.CONTENTS_STREAM_CODEC.decode(buf)
            val espe = buf.readInt()
            val result = ItemStackTemplate.STREAM_CODEC.decode(buf)
            return MithrilineFurnaceRecipe(input, espe, result)
        }
    }
}
