package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.common.recipe.MagicTableRecipe
import com.algorithmlx.ecr.common.recipe.MithrilineFurnaceRecipe
import com.algorithmlx.ecr.common.recipe.RadiatingChamberRecipe
import com.algorithmlx.ecr.common.recipe.StructureRecipe
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.crafting.display.RecipeDisplay

object RecipeDisplayTypeRegistry : RegistrationHandler(ModId) {
    private val mithrilineFurnaceReference =
        registerNoEntry(ECRModIDs.MITHRILINE_FURNACE, BuiltInRegistries.RECIPE_DISPLAY) {
            RecipeDisplay.Type(
                MithrilineFurnaceRecipe.Display.MAP_CODEC,
                MithrilineFurnaceRecipe.Display.STREAM_CODEC,
            )
        }
    private val radiatingChamberReference =
        registerNoEntry(ECRModIDs.RADIATING_CHAMBER, BuiltInRegistries.RECIPE_DISPLAY) {
            RecipeDisplay.Type(
                RadiatingChamberRecipe.Display.MAP_CODEC,
                RadiatingChamberRecipe.Display.STREAM_CODEC,
            )
        }
    private val structureReference = registerNoEntry(ECRModIDs.STRUCTURE, BuiltInRegistries.RECIPE_DISPLAY) {
        RecipeDisplay.Type(StructureRecipe.Display.MAP_CODEC, StructureRecipe.Display.STREAM_CODEC)
    }
    private val magicTableReference = registerNoEntry(ECRModIDs.MAGIC_TABLE, BuiltInRegistries.RECIPE_DISPLAY) {
        RecipeDisplay.Type(MagicTableRecipe.Display.MAP_CODEC, MagicTableRecipe.Display.STREAM_CODEC)
    }

    val mithrilineFurnace get() = mithrilineFurnaceReference.get()
    val radiatingChamber get() = radiatingChamberReference.get()
    val structure get() = structureReference.get()
    val magicTable get() = magicTableReference.get()
}
