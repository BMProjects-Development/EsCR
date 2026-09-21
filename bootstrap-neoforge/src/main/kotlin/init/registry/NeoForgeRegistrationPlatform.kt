package com.algorithmlx.ecr.neoforge.init.registry

import com.algorithmlx.ecr.api.registries.PlatformRegistryHolder
import com.algorithmlx.ecr.api.registries.RegistrationPlatform
import net.minecraft.core.Registry
import net.minecraft.resources.Identifier
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.registries.DeferredHolder
import net.neoforged.neoforge.registries.RegisterEvent
import java.util.function.Supplier

class NeoForgeRegistrationPlatform(bus: IEventBus) : RegistrationPlatform {
    private val registrations = mutableListOf<PendingRegistration>()
    private val registrationKeys = mutableSetOf<RegistrationKey>()
    private var registrationStarted = false

    init {
        bus.addListener(::onRegister)
    }

    override fun <R : Any, T : R> register(
        id: Identifier,
        registry: Registry<R>,
        factory: Supplier<T>
    ): Supplier<T> = synchronized(this) {
        declare(id, registry, factory)
    }

    override fun <R : Any, T : R> registerHolder(
        id: Identifier,
        registry: Registry<R>,
        factory: Supplier<T>
    ): PlatformRegistryHolder<R, T> = synchronized(this) {
        val holder = declare(id, registry, factory)
        PlatformRegistryHolder(holder, holder)
    }

    private fun <R : Any, T : R> declare(
        id: Identifier,
        registry: Registry<R>,
        factory: Supplier<T>
    ): DeferredHolder<R, T> {
        check(!registrationStarted) { "Cannot declare $id after NeoForge registry events have started" }

        val key = RegistrationKey(registry.key().identifier(), id)
        require(registrationKeys.add(key)) {
            "Duplicate registration ${key.entry} in registry ${key.registry}"
        }

        registrations += TypedPendingRegistration(id, registry, factory)
        return DeferredHolder.create(registry.key(), id)
    }

    private fun onRegister(event: RegisterEvent) {
        val pending = synchronized(this) {
            registrationStarted = true
            registrations.filter { it.registryKey == event.registryKey.identifier() }
        }
        pending.forEach { it.register(event) }
    }

    private data class RegistrationKey(val registry: Identifier, val entry: Identifier)

    private interface PendingRegistration {
        val registryKey: Identifier

        fun register(event: RegisterEvent)
    }

    private class TypedPendingRegistration<R : Any, T : R>(
        private val id: Identifier,
        private val registry: Registry<R>,
        private val factory: Supplier<T>
    ) : PendingRegistration {
        override val registryKey: Identifier = registry.key().identifier()

        override fun register(event: RegisterEvent) {
            event.register(registry.key(), id, Supplier<R> { factory.get() })
        }
    }
}
