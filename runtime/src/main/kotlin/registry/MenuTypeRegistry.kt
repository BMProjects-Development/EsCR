package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.menu.MenuTypeData
import com.algorithmlx.ecr.api.menu.menuType
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.common.menu.EnrichmentChamberControllerMenu
import com.algorithmlx.ecr.common.menu.EnrichmentChamberReceiverMenu
import com.algorithmlx.ecr.common.menu.HeatGeneratorMenu
import com.algorithmlx.ecr.common.menu.MagicTableMenu
import com.algorithmlx.ecr.common.menu.MagicalTeleporterMenu
import com.algorithmlx.ecr.common.menu.MatrixDestructorMenu
import com.algorithmlx.ecr.common.menu.MithrilineFurnaceMenu
import com.algorithmlx.ecr.common.menu.RadiatingChamberMenu
import com.algorithmlx.ecr.common.menu.RayTowerMenu
import com.algorithmlx.ecr.common.menu.MIMMenu
import com.algorithmlx.ecr.common.menu.MIMMenuData
import com.algorithmlx.ecr.common.menu.CraftingFrameMenu
import com.algorithmlx.ecr.common.menu.MIMFilterMenu
import net.minecraft.core.registries.BuiltInRegistries

object MenuTypeRegistry : RegistrationHandler(ModId) {
    private val mimReference = registerNoEntry(ECRModIDs.MIM_MENU, BuiltInRegistries.MENU) { menuType(MIMMenuData.CODEC, ::MIMMenu) }
    private val craftingFrameReference = registerNoEntry(ECRModIDs.CRAFTING_FRAME, BuiltInRegistries.MENU) { menuType(::CraftingFrameMenu) }
    private val mimFilterReference = registerNoEntry(ECRModIDs.MIM_FILTER, BuiltInRegistries.MENU) { menuType(::MIMFilterMenu) }
    val mim get() = mimReference.get()
    val craftingFrame get() = craftingFrameReference.get()
    val mimFilter get() = mimFilterReference.get()
    private val heatGeneratorReference = registerNoEntry(ECRModIDs.HEAT_GENERATOR, BuiltInRegistries.MENU) {
        menuType(MenuTypeData.codec, ::HeatGeneratorMenu)
    }
    private val mithrilineFurnaceReference = registerNoEntry(ECRModIDs.MITHRILINE_FURNACE, BuiltInRegistries.MENU) {
        menuType(MenuTypeData.codec, ::MithrilineFurnaceMenu)
    }
    private val radiatingChamberReference = registerNoEntry(ECRModIDs.RADIATING_CHAMBER, BuiltInRegistries.MENU) {
        menuType(MenuTypeData.codec, ::RadiatingChamberMenu)
    }
    private val magicTableReference = registerNoEntry(ECRModIDs.MAGIC_TABLE, BuiltInRegistries.MENU) {
        menuType(MenuTypeData.codec, ::MagicTableMenu)
    }
    private val matrixDestructorReference = registerNoEntry(ECRModIDs.MATRIX_DESTRUCTOR, BuiltInRegistries.MENU) {
        menuType(MenuTypeData.codec, ::MatrixDestructorMenu)
    }
    private val enrichmentChamberControllerReference = registerNoEntry(ECRModIDs.ENRICHMENT_CHAMBER_CONTROLLER, BuiltInRegistries.MENU) {
        menuType(MenuTypeData.codec, ::EnrichmentChamberControllerMenu)
    }
    private val enrichmentChamberReceiverReference = registerNoEntry(ECRModIDs.ENRICHMENT_CHAMBER_RECEIVER, BuiltInRegistries.MENU) {
        menuType(::EnrichmentChamberReceiverMenu)
    }
    private val rayTowerReference = registerNoEntry(ECRModIDs.RAY_TOWER, BuiltInRegistries.MENU) {
        menuType(MenuTypeData.codec, ::RayTowerMenu)
    }
    private val magicalTeleporterReference = registerNoEntry(ECRModIDs.MAGICAL_TELEPORTER, BuiltInRegistries.MENU) {
        menuType(MenuTypeData.codec, ::MagicalTeleporterMenu)
    }

    val heatGenerator get() = heatGeneratorReference.get()
    val mithrilineFurnace get() = mithrilineFurnaceReference.get()
    val radiatingChamber get() = radiatingChamberReference.get()
    val magicTable get() = magicTableReference.get()
    val matrixDestructor get() = matrixDestructorReference.get()
    val enrichmentChamberController get() = enrichmentChamberControllerReference.get()
    val enrichmentChamberReceiver get() = enrichmentChamberReceiverReference.get()
    val rayTower get() = rayTowerReference.get()
    val magicalTeleporter get() = magicalTeleporterReference.get()
}
