package com.algorithmlx.ecr.fabric.api

import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.init.ECRModIDs
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredient
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer
import net.minecraft.core.Holder
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.display.SlotDisplay
import java.util.stream.Stream

@Suppress("DEPRECATION")
class CountIngredient(
    private val base: Ingredient,
    val count: Int
): CustomIngredient {
    override fun test(stack: ItemStack): Boolean = count <= stack.count && base.test(stack)

    override fun items(): Stream<Holder<Item>> = base.items()

    override fun requiresTesting(): Boolean = true

    override fun getSerializer(): CustomIngredientSerializer<*> = SERIALIZER

    override fun display(): SlotDisplay = SlotDisplay.Composite(base.items().map(this::entryDisplay).toList())

    private fun entryDisplay(holder: Holder<Item>): SlotDisplay =
        SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate(holder.value(), this.count))

    class Serializer: CustomIngredientSerializer<CountIngredient> {
        override fun getIdentifier(): Identifier = ECRModIDs.COUNT.ecRL

        override fun getCodec(): MapCodec<CountIngredient> = RecordCodecBuilder.mapCodec {
            it.group(
                Ingredient.CODEC.fieldOf("base").forGetter(CountIngredient::base),
                Codec.INT.fieldOf("count").forGetter(CountIngredient::count),
                Codec.STRING.optionalFieldOf("neoforge:ingredient_type", "").forGetter { "" }
            ).apply(it) { ingredient, count, _ ->
                CountIngredient(ingredient, count)
            }
        }

        override fun getStreamCodec(): StreamCodec<RegistryFriendlyByteBuf, CountIngredient> = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, CountIngredient::base,
            ByteBufCodecs.INT, CountIngredient::count,
            ::CountIngredient
        )
    }

    companion object {
        @JvmField
        val SERIALIZER = Serializer()
    }
}
