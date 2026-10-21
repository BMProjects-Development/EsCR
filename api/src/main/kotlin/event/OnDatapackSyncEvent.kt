package com.algorithmlx.ecr.api.event

import com.algorithmlx.ecr.api.event.engine.Event
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.players.PlayerList
import net.minecraft.world.item.crafting.RecipeType

class OnDatapackSyncEvent(
    val playerList: PlayerList,
    val player: ServerPlayer?
) : Event() {
    private val mutableRecipeTypes = linkedSetOf<RecipeType<*>>()

    val relevantPlayers: Sequence<ServerPlayer>
        get() = player?.let(::sequenceOf) ?: playerList.players.asSequence()

    val recipeTypesToSend: Set<RecipeType<*>>
        get() = mutableRecipeTypes.toSet()

    fun sendRecipes(vararg recipeTypes: RecipeType<*>) {
        mutableRecipeTypes += recipeTypes
    }

    fun sendRecipes(recipeTypes: Iterable<RecipeType<*>>) {
        mutableRecipeTypes += recipeTypes
    }
}
