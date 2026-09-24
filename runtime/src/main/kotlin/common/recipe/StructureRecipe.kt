package com.algorithmlx.ecr.common.recipe

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.multiblock.Multiblock
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.registry.RecipeDisplayTypeRegistry
import com.algorithmlx.ecr.registry.RecipeSerializerRegistry
import com.algorithmlx.ecr.registry.RecipeTypeRegistry
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import io.netty.buffer.ByteBuf
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.PlacementInfo
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeBookCategories
import net.minecraft.world.item.crafting.RecipeBookCategory
import net.minecraft.world.item.crafting.RecipeSerializer
import net.minecraft.world.item.crafting.RecipeType
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.item.crafting.display.RecipeDisplay
import net.minecraft.world.item.crafting.display.SlotDisplay
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import java.util.Optional
import kotlin.jvm.optionals.getOrNull

class StructureRecipe(
    val multiblockId: Identifier,
    val time: Int,
    val ingredient: Ingredient,
    private val result: Optional<ItemStackTemplate>,
    val chance: Range,
    val structureCenter: Block?,
    val blockForPlace: Block?,
    val consumeStructure: Boolean = false
) : Recipe<SingleRecipeInput> {
    val multiblock: Multiblock
        get() =
            MultiblockDefinitions[multiblockId]
                ?: error("Multiblock $multiblockId is not loaded")

    constructor(
        multiblock: Multiblock,
        time: Int,
        ingredient: Ingredient,
        result: Optional<ItemStackTemplate>,
        chance: Range,
        structureCenter: Block?,
        blockForPlace: Block?,
        consumeStructure: Boolean = false
    ) : this(
        requireNotNull(MultiblockDefinitions.id(multiblock)) { "Multiblock is not registered" },
        time,
        ingredient,
        result,
        chance,
        structureCenter,
        blockForPlace,
        consumeStructure
    )

    init {
        require(blockForPlace != null || result.isPresent) {
            "Item result and block place is not present. One of values must exists."
        }
        require(!(result.isPresent && blockForPlace != null)) { "Item result and block place cannot be specified at the same time." }
        val definition = MultiblockDefinitions[multiblockId]
        require(
            structureCenter == null || definition == null ||
                definition.variants
                    .asSequence()
                    .flatMap { it.blocks.asSequence() }
                    .map { it.default() }
                    .any { it.`is`(structureCenter) }
        ) {
            "Structure center is not contained in $multiblockId"
        }
    }

    override fun getSerializer(): RecipeSerializer<StructureRecipe> = RecipeSerializerRegistry.structure

    override fun getType(): RecipeType<StructureRecipe> = RecipeTypeRegistry.structure.get()

    override fun placementInfo(): PlacementInfo = PlacementInfo.NOT_PLACEABLE

    override fun matches(
        input: SingleRecipeInput,
        level: Level
    ): Boolean = ingredient.test(input.item())

    override fun assemble(input: SingleRecipeInput): ItemStack = result.getOrNull()?.create() ?: ItemStack.EMPTY

    override fun showNotification(): Boolean = false

    override fun group(): String = "$ModId:${ECRModIDs.STRUCTURE}"

    override fun recipeBookCategory(): RecipeBookCategory = RecipeBookCategories.CAMPFIRE

    override fun display(): List<RecipeDisplay> =
        listOf(
            Display(
                this.ingredient.display(),
                if (blockForPlace != null) {
                    SlotDisplay.ItemSlotDisplay(blockForPlace.asItem())
                } else {
                    SlotDisplay.ItemStackSlotDisplay(result.get())
                },
                Optional.ofNullable(structureCenter?.let { SlotDisplay.ItemSlotDisplay(it.asItem()) })
            )
        )

    data class Display(
        val ingredient: SlotDisplay,
        private val resultDisplay: SlotDisplay,
        val structureCenter: Optional<SlotDisplay>
    ) : RecipeDisplay {
        override fun result(): SlotDisplay = this.resultDisplay

        override fun craftingStation(): SlotDisplay = this.ingredient

        override fun type(): RecipeDisplay.Type<out RecipeDisplay> = RecipeDisplayTypeRegistry.structure

        companion object {
            @JvmField
            val MAP_CODEC: MapCodec<Display> =
                RecordCodecBuilder.mapCodec {
                    it
                        .group(
                            SlotDisplay.CODEC.fieldOf("input").forGetter(Display::ingredient),
                            SlotDisplay.CODEC.fieldOf("result").forGetter(Display::resultDisplay),
                            SlotDisplay.CODEC.optionalFieldOf("structure_center").forGetter(Display::structureCenter)
                        ).apply(it, ::Display)
                }

            @JvmField
            val STREAM_CODEC = StreamCodec.of(::encode, ::decode)

            private fun encode(
                buf: RegistryFriendlyByteBuf,
                display: Display
            ) {
                SlotDisplay.STREAM_CODEC.encode(buf, display.ingredient)
                SlotDisplay.STREAM_CODEC.encode(buf, display.resultDisplay)
                buf.writeOptional(display.structureCenter) { b, slotDisplay ->
                    SlotDisplay.STREAM_CODEC.encode(b as RegistryFriendlyByteBuf, slotDisplay)
                }
            }

            private fun decode(buf: RegistryFriendlyByteBuf): Display {
                val input = SlotDisplay.STREAM_CODEC.decode(buf)
                val result = SlotDisplay.STREAM_CODEC.decode(buf)
                val center = buf.readOptional { b -> SlotDisplay.STREAM_CODEC.decode(b as RegistryFriendlyByteBuf) }
                return Display(input, result, center)
            }
        }
    }

    companion object {
        @JvmField
        val CODEC: MapCodec<StructureRecipe> =
            RecordCodecBuilder.mapCodec {
                it
                    .group(
                        Identifier.CODEC
                            .fieldOf("multiblock")
                            .forGetter(StructureRecipe::multiblockId),
                        Codec.INT.fieldOf("time").forGetter(StructureRecipe::time),
                        Ingredient.CODEC.fieldOf("input").forGetter(StructureRecipe::ingredient),
                        ItemStackTemplate.CODEC.optionalFieldOf("result").forGetter(StructureRecipe::result),
                        Range.CODEC.optionalFieldOf("chance", Range(0, 0)).forGetter(StructureRecipe::chance),
                        Codec
                            .optionalField("structure_center", Identifier.CODEC, true)
                            .forGetter { fg ->
                                Optional.ofNullable(fg.structureCenter?.let { thing -> BuiltInRegistries.BLOCK.getKey(thing) })
                            },
                        Codec
                            .optionalField("placement", Identifier.CODEC, true)
                            .forGetter { fg ->
                                Optional.ofNullable(fg.blockForPlace?.let { thing -> BuiltInRegistries.BLOCK.getKey(thing) })
                            },
                        Codec.BOOL
                            .fieldOf("consume_structure")
                            .orElseGet { false }
                            .forGetter(StructureRecipe::consumeStructure)
                    ).apply(it) { multiblockId, time, ingredient, result, chance, center, placement, consumeStructure ->
                        StructureRecipe(
                            multiblockId,
                            time,
                            ingredient,
                            result,
                            chance,
                            center.getOrNull()?.let { l ->
                                BuiltInRegistries.BLOCK.getOptional(l).getOrNull()
                            },
                            placement.getOrNull()?.let { l ->
                                BuiltInRegistries.BLOCK.getOptional(l).getOrNull()
                            },
                            consumeStructure
                        )
                    }
            }

        @JvmField
        val STREAM_CODEC = StreamCodec.of(::encode, ::decode)

        private fun encode(
            buf: RegistryFriendlyByteBuf,
            recipe: StructureRecipe
        ) {
            Identifier.STREAM_CODEC.encode(buf, recipe.multiblockId)
            buf.writeInt(recipe.time)
            Ingredient.CONTENTS_STREAM_CODEC.encode(buf, recipe.ingredient)
            buf.writeOptional(recipe.result) { buffer, item ->
                ItemStackTemplate.STREAM_CODEC.encode(buffer as RegistryFriendlyByteBuf, item)
            }
            Range.STREAM_CODEC.encode(buf, recipe.chance)
            buf.writeNullable(recipe.structureCenter?.let { BuiltInRegistries.BLOCK.getKey(it) }, Identifier.STREAM_CODEC)
            buf.writeNullable(recipe.blockForPlace?.let { BuiltInRegistries.BLOCK.getKey(it) }, Identifier.STREAM_CODEC)
            buf.writeBoolean(recipe.consumeStructure)
        }

        private fun decode(buf: RegistryFriendlyByteBuf): StructureRecipe {
            val multiblockId = Identifier.STREAM_CODEC.decode(buf)
            val time = buf.readInt()
            val ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buf)
            val result = buf.readOptional { ItemStackTemplate.STREAM_CODEC.decode(it as RegistryFriendlyByteBuf) }
            val chance = Range.STREAM_CODEC.decode(buf)
            val structureCenterId = buf.readNullable { Identifier.STREAM_CODEC.decode(it) }
            val blockForPlaceId = buf.readNullable { Identifier.STREAM_CODEC.decode(it) }
            val consumeStructure = buf.readBoolean()

            val structureCenter = BuiltInRegistries.BLOCK.getOptional(structureCenterId).getOrNull()
            val blockForPlace = BuiltInRegistries.BLOCK.getOptional(blockForPlaceId).getOrNull()
            return StructureRecipe(
                multiblockId,
                time,
                ingredient,
                result,
                chance,
                structureCenter,
                blockForPlace,
                consumeStructure
            )
        }
    }

    data class Range(
        val min: Int,
        val max: Int
    ) {
        fun isEmpty() = this.min == 0 && max == 0

        companion object {
            @JvmField
            val CODEC: Codec<Range> =
                RecordCodecBuilder.create {
                    it
                        .group(
                            Codec.INT.fieldOf("min").forGetter(Range::min),
                            Codec.INT.fieldOf("max").forGetter(Range::max)
                        ).apply(it, ::Range)
                }

            @JvmField
            val STREAM_CODEC: StreamCodec<ByteBuf, Range> =
                StreamCodec.composite(
                    ByteBufCodecs.INT,
                    Range::min,
                    ByteBufCodecs.INT,
                    Range::max,
                    ::Range
                )
        }
    }
}
