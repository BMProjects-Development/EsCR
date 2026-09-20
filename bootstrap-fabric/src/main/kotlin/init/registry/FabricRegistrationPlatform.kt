package com.algorithmlx.ecr.fabric.init.registry

import com.algorithmlx.ecr.api.registries.PlatformRegistryHolder
import com.algorithmlx.ecr.api.registries.RegistrationPlatform
import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.resources.Identifier
import java.util.function.Supplier

object FabricRegistrationPlatform : RegistrationPlatform {
    override fun <R : Any, T : R> register(
        id: Identifier,
        registry: Registry<R>,
        factory: Supplier<T>,
    ): Supplier<T> {
        val registered = Registry.register(registry, id, factory.get())
        return Supplier { registered }
    }

    override fun <R : Any, T : R> registerHolder(
        id: Identifier,
        registry: Registry<R>,
        factory: Supplier<T>,
    ): PlatformRegistryHolder<R, T> {
        val registered = Registry.registerForHolder(registry, id, factory.get())
        @Suppress("UNCHECKED_CAST")
        val holder = registered as Holder<R>
        return PlatformRegistryHolder(holder, Supplier(registered::value))
    }
}
