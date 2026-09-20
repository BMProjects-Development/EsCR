package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.common.recipe.MagicTableRecipe
import com.algorithmlx.ecr.common.recipe.MithrilineFurnaceRecipe
import com.algorithmlx.ecr.common.recipe.RadiatingChamberRecipe
import com.algorithmlx.ecr.common.recipe.StructureRecipe
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.crafting.RecipeSerializer

object RecipeSerializerRegistry : RegistrationHandler(ModId) {
    private val mithrilineFurnaceReference =
        registerNoEntry(ECRModIDs.MITHRILINE_FURNACE, BuiltInRegistries.RECIPE_SERIALIZER) {
            RecipeSerializer(MithrilineFurnaceRecipe.CODEC, MithrilineFurnaceRecipe.STREAM_CODEC)
        }
    private val radiatingChamberReference =
        registerNoEntry(ECRModIDs.RADIATING_CHAMBER, BuiltInRegistries.RECIPE_SERIALIZER) {
            RecipeSerializer(RadiatingChamberRecipe.CODEC, RadiatingChamberRecipe.STREAM_CODEC)
        }
    private val structureReference = registerNoEntry(ECRModIDs.STRUCTURE, BuiltInRegistries.RECIPE_SERIALIZER) {
        RecipeSerializer(StructureRecipe.CODEC, StructureRecipe.STREAM_CODEC)
    }
    private val magicTableReference = registerNoEntry(ECRModIDs.MAGIC_TABLE, BuiltInRegistries.RECIPE_SERIALIZER) {
        RecipeSerializer(MagicTableRecipe.CODEC, MagicTableRecipe.STREAM_CODEC)
    }

    val mithrilineFurnace get() = mithrilineFurnaceReference.get()
    val radiatingChamber get() = radiatingChamberReference.get()
    val structure get() = structureReference.get()
    val magicTable get() = magicTableReference.get()
}
