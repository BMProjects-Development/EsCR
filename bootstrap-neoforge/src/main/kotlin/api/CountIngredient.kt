package com.algorithmlx.ecr.neoforge.api

import com.algorithmlx.ecr.neoforge.init.registry.IngredientRegistry
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.Holder
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemStackTemplate
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.display.SlotDisplay
import net.neoforged.neoforge.common.crafting.ICustomIngredient
import net.neoforged.neoforge.common.crafting.IngredientType
import java.util.stream.Stream

@Suppress("DEPRECATION")
class CountIngredient(private val base: Ingredient, val count: Int): ICustomIngredient {
    override fun test(stack: ItemStack): Boolean = count <= stack.count && base.test(stack)

    override fun items(): Stream<Holder<Item>> = base.items()

    override fun isSimple(): Boolean = true

    override fun getType(): IngredientType<*> = IngredientRegistry.COUNT_TYPE

    override fun display(): SlotDisplay = SlotDisplay.Composite(
        this.items().map { SlotDisplay.ItemStackSlotDisplay(ItemStackTemplate(it, count)) as SlotDisplay }.toList()
    )

    companion object {
        @JvmField
        val CODEC: MapCodec<CountIngredient> = RecordCodecBuilder.mapCodec {
            it.group(
                Ingredient.CODEC.fieldOf("base").forGetter(CountIngredient::base),
                Codec.INT.fieldOf("count").forGetter(CountIngredient::count),
                Codec.STRING.optionalFieldOf("fabric:type", "").forGetter { "" } // Ignore "fabric:type" key
            ).apply(it) { ingredient, count, _ -> CountIngredient(ingredient, count) }
        }

        @JvmField
        val STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, CountIngredient::base,
            ByteBufCodecs.INT, CountIngredient::count,
            ::CountIngredient
        )
    }
}
