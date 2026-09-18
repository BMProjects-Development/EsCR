package com.algorithmlx.ecr.common.components

import net.minecraft.world.entity.player.Player

interface PlayerMatrixStorage {
    fun getOrCreate(player: Player): PlayerMatrixComponent

    fun set(player: Player, component: PlayerMatrixComponent)

    companion object {
        @JvmStatic
        lateinit var instance: PlayerMatrixStorage
    }
}

val Player.playerMatrix: PlayerMatrixComponent
    get() = PlayerMatrixStorage.instance.getOrCreate(this)

fun Player.setPlayerMatrix(component: PlayerMatrixComponent) {
    PlayerMatrixStorage.instance.set(this, component)
}

inline fun Player.updatePlayerMatrix(update: PlayerMatrixComponent.() -> Unit): PlayerMatrixComponent {
    val updated = playerMatrix.copy().apply(update)
    setPlayerMatrix(updated)
    return updated
}
