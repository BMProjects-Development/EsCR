package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.common.recipe.MagicTableRecipe
import com.algorithmlx.ecr.common.recipe.MithrilineFurnaceRecipe
import com.algorithmlx.ecr.common.recipe.RadiatingChamberRecipe
import com.algorithmlx.ecr.common.recipe.StructureRecipe
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeType

object RecipeTypeRegistry : RegistrationHandler(ModId) {
    val mithrilineFurnace = registerSimple<MithrilineFurnaceRecipe>(ECRModIDs.MITHRILINE_FURNACE)
    val radiatingChamber = registerSimple<RadiatingChamberRecipe>(ECRModIDs.RADIATING_CHAMBER)
    val structure = registerSimple<StructureRecipe>(ECRModIDs.STRUCTURE)
    val magicTable = registerSimple<MagicTableRecipe>(ECRModIDs.MAGIC_TABLE)

    private fun <T : Recipe<*>> registerSimple(id: String) =
        registerNoEntry(id, BuiltInRegistries.RECIPE_TYPE) {
            object : RecipeType<T> {
                override fun toString(): String = id.ecRL.toString()
            }
        }
}
