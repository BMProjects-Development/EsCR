package com.algorithmlx.ecr.common.magic

object MagicDefenseThreadProtect {
    private val depth = ThreadLocal.withInitial { 0 }

    @JvmStatic
    fun active(): Boolean = depth.get() > 0

    @JvmStatic
    fun enter() {
        depth.set(depth.get() + 1)
    }

    @JvmStatic
    fun exit() {
        val value = depth.get() - 1

        if (value <= 0) depth.remove()
        else depth.set(value)
    }
}
