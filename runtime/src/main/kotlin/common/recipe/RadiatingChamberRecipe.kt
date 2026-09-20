package com.algorithmlx.ecr.common.recipe

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.mru.balance.MRUBalance
import com.algorithmlx.ecr.api.utils.count
import com.algorithmlx.ecr.common.init.ECRModIDs
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
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.PlacementInfo
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeBookCategories
import net.minecraft.world.item.crafting.RecipeBookCategory
import net.minecraft.world.item.crafting.RecipeInput
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay
import net.minecraft.world.level.Level
import java.util.Optional

class RadiatingChamberRecipe(
    val input: Ingredient,
    val secondary: Optional<Ingredient>,
    val time: Int,
    val mruPerTick: Int,
    val result: ItemStackTemplate,
    val balance: BalanceRange,
    val ignoreBalance: Boolean,
) : Recipe<RadiatingChamberRecipe.Input> {
    init {
        require(time > 0) { "Radiating chamber processing time must be positive" }
        require(mruPerTick > 0) { "Radiating chamber MRU usage must be positive" }
    }

    override fun matches(input: Input, level: Level): Boolean = matches(input)

    fun matches(input: Input): Boolean {
        if (this.input.count <= 0 || !this.input.test(input.primary) || input.primary.count < this.input.count) return false
        if (secondary.isPresent) {
            val ingredient = secondary.get()
            if (ingredient.count <= 0 || !ingredient.test(input.secondary) || input.secondary.count < ingredient.count) return false
        } else if (!input.secondary.isEmpty) return false

        return ignoreBalance || balance.matches(input.balance)
    }

    override fun assemble(input: Input): ItemStack = result.create()

    override fun showNotification(): Boolean = false

    override fun group(): String = "$ModId:${ECRModIDs.RADIATING_CHAMBER}"

    override fun getSerializer(): RecipeSerializer<out Recipe<Input>> = RecipeSerializerRegistry.radiatingChamber

    override fun getType(): RecipeType<out Recipe<Input>> = RecipeTypeRegistry.radiatingChamber.get()

    override fun placementInfo(): PlacementInfo = PlacementInfo.NOT_PLACEABLE

    override fun recipeBookCategory(): RecipeBookCategory = RecipeBookCategories.FURNACE_MISC

    override fun display(): List<RecipeDisplay> = listOf(
        Display(
            input.display(),
            secondary.map(Ingredient::display).orElse(SlotDisplay.Empty.INSTANCE),
            SlotDisplay.ItemStackSlotDisplay(result),
            SlotDisplay.ItemSlotDisplay(BlockRegistry.radiatingChamber.get().asItem()),
        ),
    )

    class Input(
        val primary: ItemStack,
        val secondary: ItemStack,
        val balance: MRUBalance,
    ) : RecipeInput {
        override fun getItem(index: Int): ItemStack = when (index) {
            0 -> primary
            1 -> secondary
            else -> throw IndexOutOfBoundsException(index)
        }

        override fun size(): Int = 2
    }

    data class BalanceRange(
        val min: Optional<Double> = Optional.empty(),
        val max: Optional<Double> = Optional.empty(),
    ) {
        init {
            require(min.isEmpty || min.get().isFinite() && min.get() in 0.0..2.0) { "Minimum balance must be between 0 and 2" }
            require(max.isEmpty || max.get().isFinite() && max.get() in 0.0..2.0) { "Maximum balance must be between 0 and 2" }
            require(min.isEmpty || max.isEmpty || min.get() < max.get()) { "Minimum balance must be below maximum balance" }
        }

        fun matches(balance: MRUBalance): Boolean = matches(balance.upperBalance) && matches(balance.lowerBalance)

        fun matches(value: Double): Boolean = value.isFinite() && value in 0.0..2.0 &&
            (min.isEmpty || value > min.get()) && (max.isEmpty || value < max.get())

        companion object {
            @JvmField
            val CODEC: Codec<BalanceRange> = RecordCodecBuilder.create {
                it.group(
                    Codec.doubleRange(0.0, 2.0).optionalFieldOf("min").forGetter(BalanceRange::min),
                    Codec.doubleRange(0.0, 2.0).optionalFieldOf("max").forGetter(BalanceRange::max),
                ).apply(it, ::BalanceRange)
            }
        }
    }

    @JvmRecord
    data class Display(
        val input: SlotDisplay,
        val secondary: SlotDisplay,
        private val resultDisplay: SlotDisplay,
        private val station: SlotDisplay,
    ) : RecipeDisplay {
        override fun result(): SlotDisplay = resultDisplay

        override fun craftingStation(): SlotDisplay = station

        override fun type(): RecipeDisplay.Type<out RecipeDisplay> = RecipeDisplayTypeRegistry.radiatingChamber

        companion object {
            @JvmField
            val MAP_CODEC: MapCodec<Display> = RecordCodecBuilder.mapCodec {
                it.group(
                    SlotDisplay.CODEC.fieldOf("input").forGetter(Display::input),
                    SlotDisplay.CODEC.fieldOf("secondary").forGetter(Display::secondary),
                    SlotDisplay.CODEC.fieldOf("result").forGetter(Display::resultDisplay),
                    SlotDisplay.CODEC.fieldOf("station").forGetter(Display::station),
                ).apply(it, ::Display)
            }

            @JvmField
            val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, Display> = StreamCodec.composite(
                SlotDisplay.STREAM_CODEC,
                Display::input,
                SlotDisplay.STREAM_CODEC,
                Display::secondary,
                SlotDisplay.STREAM_CODEC,
                Display::resultDisplay,
                SlotDisplay.STREAM_CODEC,
                Display::station,
                ::Display,
            )
        }
    }

    companion object {
        @JvmField
        val CODEC: MapCodec<RadiatingChamberRecipe> = RecordCodecBuilder.mapCodec {
            it.group(
                Ingredient.CODEC.fieldOf("input").forGetter(RadiatingChamberRecipe::input),
                Ingredient.CODEC.optionalFieldOf("secondary").forGetter(RadiatingChamberRecipe::secondary),
                Codec.intRange(1, Int.MAX_VALUE).fieldOf("time").forGetter(RadiatingChamberRecipe::time),
                Codec.intRange(1, Int.MAX_VALUE).optionalFieldOf("mru", 1).forGetter(RadiatingChamberRecipe::mruPerTick),
                ItemStackTemplate.MAP_CODEC.fieldOf("result").forGetter(RadiatingChamberRecipe::result),
                BalanceRange.CODEC.optionalFieldOf("balance", BalanceRange()).forGetter(RadiatingChamberRecipe::balance),
                Codec.BOOL.optionalFieldOf("ignore_balance", false).forGetter(RadiatingChamberRecipe::ignoreBalance),
            ).apply(it, ::RadiatingChamberRecipe)
        }

        @JvmField
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, RadiatingChamberRecipe> = StreamCodec.of(::encode, ::decode)

        private fun encode(buf: RegistryFriendlyByteBuf, recipe: RadiatingChamberRecipe) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.input)
            Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.encode(buf, recipe.secondary)
            buf.writeInt(recipe.time)
            buf.writeInt(recipe.mruPerTick)
            ItemStackTemplate.STREAM_CODEC.encode(buf, recipe.result)
            buf.writeOptional(recipe.balance.min) { buffer, value -> buffer.writeDouble(value) }
            buf.writeOptional(recipe.balance.max) { buffer, value -> buffer.writeDouble(value) }
            buf.writeBoolean(recipe.ignoreBalance)
        }

        private fun decode(buf: RegistryFriendlyByteBuf): RadiatingChamberRecipe {
            val input = Ingredient.CONTENTS_STREAM_CODEC.decode(buf)
            val secondary = Ingredient.OPTIONAL_CONTENTS_STREAM_CODEC.decode(buf)
            val time = buf.readInt()
            val mru = buf.readInt()
            val result = ItemStackTemplate.STREAM_CODEC.decode(buf)
            val balance = BalanceRange(buf.readOptional { it.readDouble() }, buf.readOptional { it.readDouble() })
            return RadiatingChamberRecipe(input, secondary, time, mru, result, balance, buf.readBoolean())
        }
    }
}
