package com.algorithmlx.ecr.common.init

import com.algorithmlx.ecr.api.utils.ecRL

object ECRModIDs {
    // Block Type Codecs
    const val CLUSTER = "cluster"
    const val CRYSTAL = "crystal"

    // Universal
    const val SOLAR_PRISM = "solar_prism"
    const val RADIATING_CHAMBER = "radiating_chamber"
    const val MITHRILINE_FURNACE = "mithriline_furnace"
    const val MITHRILINE_CRYSTAL = "mithriline_crystal"
    const val MATRIX_DESTRUCTOR = "matrix_destructor"
    const val SOUL_STONE = "soul_stone"
    const val BOUND_GEM = "bound_gem"
    const val MAGIC_TABLE = "magic_table"
    const val COLD_DISTILLER = "cold_distiller"
    const val HEAT_GENERATOR = "heat_generator"
    const val MAGICAL_TELEPORTER = "magical_teleporter"
    const val ENRICHMENT_CHAMBER = "enrichment_chamber"
    const val ENRICHMENT_CHAMBER_CONTROLLER = "${ENRICHMENT_CHAMBER}_controller"
    const val ENRICHMENT_CHAMBER_EXTRACTOR = "${ENRICHMENT_CHAMBER}_extractor"
    const val ENRICHMENT_CHAMBER_RECEIVER = "${ENRICHMENT_CHAMBER}_receiver"
    const val ASSEMBLED_MULTIBLOCK_PART = "assembled_multiblock_part"
    const val RAY_TOWER = "ray_tower"
    const val SUN_ABSORBER = "sun_absorber"
    const val CREATIVE_MRU_SOURCE = "creative_mru_source"

    // Blocks
    const val MITHRILINE_PLATING = "mithriline_plating"
    const val VOID_STONE = "void_stone"
    const val PALE_BLOCK = "pale_block"
    const val PALE_PLATING = "pale_plating"
    const val MAGIC_PLATING = "magic_plating"
    const val DEMONIC_PLATING = "demonic_plating"
    const val FORTIFIED_STONE = "fortified_stone"
    const val FLAME_CLUSTER = "flame_cluster"
    const val WATER_CLUSTER = "water_cluster"
    const val EARTH_CLUSTER = "earth_cluster"
    const val AIR_CLUSTER = "air_cluster"
    const val ENRICHMENT_CHAMBER_HOLDER = "${ENRICHMENT_CHAMBER}_holder"
    const val FORTIFIED_GLASS = "fortified_glass"
    const val RAY_TOWER_BASE = "${RAY_TOWER}_base"
    const val MITHRILINE_ORE = "mithriline_ore"
    const val DEEPSLATE_MITHRILINE_ORE = "deepslate_$MITHRILINE_ORE"

    // Data Components / Attachments
    const val BOOK_TYPE = "book_type"
    const val PLAYER_MATRIX = "player_matrix"

    // BookTypes
    const val BASIC = "basic"
    const val MRU = "mru"
    const val ENGINEER = "engineer"
    const val HOANNA = "hoanna"
    const val SHADE = "shade"

    // Items
    const val HAMMER = "hammer"
    const val RESEARCH_BOOK = "research_book"

    const val WEAKNESS_ELEMENTAL_AXE = "weakness_elemental_axe"
    const val WEAKNESS_ELEMENTAL_HOE = "weakness_elemental_hoe"
    const val WEAKNESS_ELEMENTAL_PICKAXE = "weakness_elemental_pickaxe"
    const val WEAKNESS_ELEMENTAL_SHOVEL = "weakness_elemental_shovel"
    const val WEAKNESS_ELEMENTAL_SWORD = "weakness_elemental_sword"

    const val ELEMENTAL_GEM = "elemental_gem"
    const val FLAME_GEM = "flame_gem"
    const val WATER_GEM = "water_gem"
    const val EARTH_GEM = "earth_gem"
    const val AIR_GEM = "air_gem"

    const val ELEMENTAL_CORE = "elemental_core"
    const val COMBINED_MAGIC_ALLOYS = "combined_magic_alloys"
    const val DEMONIC_CORE = "demonic_core"
    const val DIAMOND_PLATE = "diamond_plate"
    const val EMERALD_PLATE = "emerald_plate"
    const val ENDER_SCALE_ALLOY = "ender_scale_alloy"
    const val FORCEFIELD_CORE = "forcefield_core"
    const val FORCIFIELD_PLATING = "forcefield_plating"
    const val FORTIFIED_FRAME = "fortified_frame"
    const val FORTIFIED_PLATE = "fortified_plate"
    const val MAGIC_PLATE = "magic_plate"
    const val MAGIC_PURIFIED_BLAZE_ALLOY = "magic_purified_blaze_alloy"
    const val MAGIC_PURIFIED_ENDER_SCALE_ALLOY = "magic_purified_ender_scale_alloy"
    const val MAGIC_PURIFIED_GLASS_ALLOY = "magic_purified_glass_alloy"
    const val OBSIDIAN_PLATE = "obsidian_plate"
    const val PALE_CORE = "pale_core"
    const val PALE_PLATE = "pale_plate"
    const val PARTICLE_CATCHER = "particle_catcher"
    const val PARTICLE_EMITTER = "particle_emitter"
    const val SUN_IMBUED_GLASS = "sun_imbued_glass"
    const val VOID_PLATING = "void_plating"
    const val MITHRILINE_INGOT = "mithriline_ingot"
    const val MAGICAL_INGOT = "magical_ingot"
    const val MAGICAL_SLAG = "magical_slag"
    const val MITHRILINE_DUST = "mithriline_dust"
    const val HEATING_ROD = "heating_rod"
    const val MITHRILINE_CRYSTAL_GEM = "mithriline_crystal_gem"
    const val MRU_RESONATING_CRYSTAL = "mru_resonating_crystal"
    const val FADING_CRYSTAL = "fading_crystal"
    const val EYE_OF_ABSORPTION = "eye_of_absorption"
    const val HEAT_CORE = "heat_core"
    const val MONOCLE = "monocle"

    // MRU Types
    const val UBMRU = "ubmru"
    const val ESPE = "espe"

    // Multiblock Matcher
    const val TAG = "tag"
    const val BLOCK = "block"
    const val LIST = "list"

    // Multiblocks
    const val FLAME_CRYSTAL = "flame_crystal"
    const val WATER_CRYSTAL = "water_crystal"
    const val EARTH_CRYSTAL = "earth_crystal"
    const val AIR_CRYSTAL = "air_crystal"
    const val LIGHTNING_COLLECTOR = "lightning_collector"

    // Recipes
    const val STRUCTURE = "structure"

    // Creative Tabs
    const val TAB_ITEMS = "tab_items"
    const val TAB_BLOCKS = "tab_blocks"

    // Ingredients
    const val COUNT = "count"

    // Effects, Enchantments and other Magic objects
    const val MRU_CORRUPTION = "${MRU}_corruption"

    const val MAGIC_DEFENSE = "magic_defense"

    const val MAGIC_BREAK = "magic_break"

    fun guiLocation(id: String) = textureLocation("gui/$id")

    fun textureLocation(id: String) = "textures/$id.png".ecRL
}
