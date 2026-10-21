package com.algorithmlx.ecr.api.utils

interface PlatformUtils {
    fun isLoaded(modId: String): Boolean

    companion object {
        lateinit var instance: PlatformUtils
    }
}

fun isLoaded(modId: String): Boolean = PlatformUtils.instance.isLoaded(modId)
