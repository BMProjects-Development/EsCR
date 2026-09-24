package com.algorithmlx.ecr.neoforge.init.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.neoforge.api.CountIngredient
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.crafting.IngredientType
import net.neoforged.neoforge.registries.DeferredRegister
import net.neoforged.neoforge.registries.NeoForgeRegistries

object IngredientRegistry {
    private val registry = DeferredRegister.create(NeoForgeRegistries.INGREDIENT_TYPES, ModId)

    fun init(bus: IEventBus) {
        registry.register(bus)
    }

    val COUNT_TYPE: IngredientType<CountIngredient> by register(ECRModIDs.COUNT) { IngredientType(CountIngredient.CODEC, CountIngredient.STREAM_CODEC) }

    private fun register(id: String, factory: () -> IngredientType<CountIngredient>) = registry.register(id) { _ -> factory() }
}
