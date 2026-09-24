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
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.PlacementInfo
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeBookCategories
import net.minecraft.world.item.crafting.RecipeBookCategory
import net.minecraft.world.item.crafting.RecipeInput
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.ShapedRecipePattern
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay
import net.minecraft.world.level.Level
import java.util.Optional
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull

class MagicTableRecipe(
    val input: Optional<ShapedRecipePattern>,
    val catalyst: Optional<Ingredient>,
    val time: Int,
    val mruPerTick: Int,
    val result: ItemStackTemplate
) : Recipe<MagicTableRecipe.Input> {
    init {
        require(this@MagicTableRecipe.input.isPresent || catalyst.isPresent) { "Recipe must present with inputs or catalyst!" }
        require(
            this@MagicTableRecipe.input.isEmpty ||
                this@MagicTableRecipe.input.get().height() * this@MagicTableRecipe.input.get().width() <= CRAFTING_SLOT_COUNT
        ) {
            "Recipe max have only ~2x2 recipe grid"
        }
    }

    override fun matches(
        input: Input,
        level: Level
    ): Boolean {
        if (this@MagicTableRecipe.input.isPresent) {
            val shaped = this@MagicTableRecipe.input.get()
            val craftingInput = input.craftingInput().input()

            if (!shaped.matches(craftingInput)) return false
        } else if (input.craftingItems.any { !it.isEmpty }) {
            return false
        }

        val catalystStack = input.catalystStack
        return if (catalyst.isPresent) catalyst.get().test(catalystStack) else catalystStack.isEmpty
    }

    override fun assemble(input: Input): ItemStack = this.result.create()

    override fun showNotification(): Boolean = true

    override fun group(): String = "$ModId:${ECRModIDs.MAGIC_TABLE}"

    override fun getSerializer(): RecipeSerializer<out Recipe<Input>> = RecipeSerializerRegistry.magicTable

    override fun getType(): RecipeType<out Recipe<Input>> = RecipeTypeRegistry.magicTable.get()

    override fun placementInfo(): PlacementInfo = PlacementInfo.NOT_PLACEABLE

    override fun recipeBookCategory(): RecipeBookCategory = RecipeBookCategories.CAMPFIRE

    override fun display(): List<RecipeDisplay> =
        listOf(
            Display(
                Optional.ofNullable(
                    input
                        .getOrNull()
                        ?.ingredients()
                        ?.map {
                            it
                                .map(Ingredient::display)
                                .getOrElse { SlotDisplay.Empty.INSTANCE }
                        }
                ),
                Optional.ofNullable(catalyst.getOrNull()?.display()),
                SlotDisplay.ItemStackSlotDisplay(result),
                SlotDisplay.ItemSlotDisplay(BlockRegistry.magicTable.get().asItem())
            )
        )

    class Input(
        private val stacks: List<ItemStack>
    ) : RecipeInput {
        init {
            require(stacks.size == INPUT_SLOT_COUNT) { "Envoyer recipe input must contain $INPUT_SLOT_COUNT slots" }
        }

        val craftingItems: List<ItemStack> get() = stacks.subList(0, CRAFTING_SLOT_COUNT)
        val catalystStack: ItemStack get() = stacks[CATALYST_SLOT]

        override fun getItem(index: Int): ItemStack = stacks[index]

        override fun size(): Int = stacks.size

        fun craftingInput(): CraftingInput.Positioned =
            CraftingInput.ofPositioned(
                CRAFTING_WIDTH,
                CRAFTING_HEIGHT,
                craftingItems
            )
    }

    @JvmRecord
    data class Display(
        val input: Optional<List<SlotDisplay>>,
        val catalyst: Optional<SlotDisplay>,
        private val resultDisplay: SlotDisplay,
        private val station: SlotDisplay
    ) : RecipeDisplay {
        override fun result(): SlotDisplay = this.resultDisplay

        override fun craftingStation(): SlotDisplay = this.station

        override fun type(): RecipeDisplay.Type<out RecipeDisplay> = RecipeDisplayTypeRegistry.magicTable

        companion object {
            @JvmField
            val MAP_CODEC: MapCodec<Display> =
                RecordCodecBuilder.mapCodec {
                    it
                        .group(
                            SlotDisplay.CODEC
                                .listOf()
                                .optionalFieldOf("input")
                                .forGetter(Display::input),
                            SlotDisplay.CODEC.optionalFieldOf("catalyst").forGetter(Display::catalyst),
                            SlotDisplay.CODEC.fieldOf("result").forGetter(Display::resultDisplay),
                            SlotDisplay.CODEC.fieldOf("station").forGetter(Display::station)
                        ).apply(it, ::Display)
                }

            @JvmField
            val STREAM_CODEC =
                StreamCodec.composite(
                    ByteBufCodecs.optional(
                        ByteBufCodecs.list<RegistryFriendlyByteBuf, SlotDisplay>().apply(SlotDisplay.STREAM_CODEC)
                    ),
                    Display::input,
                    ByteBufCodecs.optional(SlotDisplay.STREAM_CODEC),
                    Display::catalyst,
                    SlotDisplay.STREAM_CODEC,
                    Display::resultDisplay,
                    SlotDisplay.STREAM_CODEC,
                    Display::station,
                    ::Display
                )
        }
    }

    companion object {
        private const val CRAFTING_WIDTH = 2
        private const val CRAFTING_HEIGHT = 2
        private const val CRAFTING_SLOT_COUNT = CRAFTING_WIDTH * CRAFTING_HEIGHT
        private const val CATALYST_SLOT = CRAFTING_SLOT_COUNT
        private const val INPUT_SLOT_COUNT = CRAFTING_SLOT_COUNT + 1

        @JvmField
        val CODEC: MapCodec<MagicTableRecipe> =
            RecordCodecBuilder.mapCodec {
                it
                    .group(
                        ShapedRecipePattern.MAP_CODEC
                            .codec()
                            .optionalFieldOf("input")
                            .forGetter(MagicTableRecipe::input),
                        Ingredient.CODEC.optionalFieldOf("catalyst").forGetter(MagicTableRecipe::catalyst),
                        Codec.INT.fieldOf("time").forGetter(MagicTableRecipe::time),
                        Codec.INT.fieldOf("mru").forGetter(MagicTableRecipe::mruPerTick),
                        ItemStackTemplate.MAP_CODEC.fieldOf("result").forGetter(MagicTableRecipe::result)
                    ).apply(it, ::MagicTableRecipe)
            }

        @JvmField
        val STREAM_CODEC = StreamCodec.of(::encode, ::decode)

        private fun encode(
            buf: RegistryFriendlyByteBuf,
            recipe: MagicTableRecipe
        ) {
            buf.writeOptional(recipe.input) { b, pattern ->
                ShapedRecipePattern.STREAM_CODEC.encode(b as RegistryFriendlyByteBuf, pattern)
            }
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.encode(buf, recipe.catalyst)
            buf.writeInt(recipe.time)
            buf.writeInt(recipe.mruPerTick)
            ItemStackTemplate.STREAM_CODEC.encode(buf, recipe.result)
        }

        private fun decode(buf: RegistryFriendlyByteBuf): MagicTableRecipe {
            val pattern = buf.readOptional { ShapedRecipePattern.STREAM_CODEC.decode(it as RegistryFriendlyByteBuf) }
            val catalyst = Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.decode(buf)
            val time = buf.readInt()
            val mru = buf.readInt()
            val result = ItemStackTemplate.STREAM_CODEC.decode(buf)
            return MagicTableRecipe(pattern, catalyst, time, mru, result)
        }
    }
}
