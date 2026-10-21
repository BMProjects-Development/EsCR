package com.algorithmlx.ecr.api.mru

import net.minecraft.network.chat.Component

interface PlayerMatrixType {
    val displayName: Component

    val reduceRadiationMultiplier: Double

    val protectMatrixDecay: Boolean
}
