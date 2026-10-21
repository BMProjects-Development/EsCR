package com.algorithmlx.ecr.api.utils

import com.algorithmlx.ecr.api.ModId
import net.minecraft.resources.Identifier

val String.rl get() = Identifier.parse(this)
val String.ecPrefix get() = "$ModId:$this"
val String.ecRL: Identifier
    get() = this.ecPrefix.rl

fun makeIntArray(value: Int = 0) = intArrayOf(value)
