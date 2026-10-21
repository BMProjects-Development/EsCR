package com.algorithmlx.ecr.neoforge.init.registry

import java.util.function.Supplier
import kotlin.reflect.KProperty

internal operator fun <T> Supplier<T>.getValue(thisRef: Any?, property: KProperty<*>): T = get()
