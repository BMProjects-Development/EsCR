package com.algorithmlx.ecr.common.components

import com.algorithmlx.ecr.registry.AttachmentRegistry
import net.minecraft.world.entity.player.Player

val Player.playerMatrix: PlayerMatrixComponent
    get() = AttachmentRegistry.playerMatrix.getOrCreate(this)

fun Player.setPlayerMatrix(component: PlayerMatrixComponent) {
    AttachmentRegistry.playerMatrix.set(this, component)
}

inline fun Player.updatePlayerMatrix(update: PlayerMatrixComponent.() -> Unit): PlayerMatrixComponent {
    val updated = playerMatrix.copy().apply(update)
    setPlayerMatrix(updated)
    return updated
}
