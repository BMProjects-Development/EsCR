package com.algorithmlx.ecr.api.registries

import net.minecraft.core.Holder
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.item.CreativeModeTab
import net.minecraft.world.item.Item
import net.minecraft.world.level.block.Block
import java.util.function.Supplier
import kotlin.reflect.KProperty

interface RegistrationLookup {
    fun <R : Any, T : R> T.registerNoEntry(id: String, registry: Registry<R>): Supplier<T>

    fun <R : Any, T : R> T.registerNoEntry(id: Identifier, registry: Registry<R>): Supplier<T>

    fun <R : Any, T : R> registerNoEntry(id: String, registry: Registry<R>, factory: () -> T): Supplier<T>

    fun <R : Any, T : R> registerNoEntry(id: Identifier, registry: Registry<R>, factory: () -> T): Supplier<T>

    fun <R : Any, T : R> T.registerHolder(id: String, registry: Registry<R>): RegistryHolder<R, T>

    fun <R : Any, T : R> T.registerHolder(id: Identifier, registry: Registry<R>): RegistryHolder<R, T>

    fun <R : Any, T : R> registerHolder(
        id: String,
        registry: Registry<R>,
        factory: () -> T,
    ): RegistryHolder<R, T>

    fun <R : Any, T : R> registerHolder(
        id: Identifier,
        registry: Registry<R>,
        factory: () -> T,
    ): RegistryHolder<R, T>

    fun init()
}

interface RegistryDefaultExtensions {
    fun self(): RegistrationLookup

    fun <T : Item> T.registerItem(id: String): Supplier<T> = with(self()) {
        registerNoEntry(id, BuiltInRegistries.ITEM)
    }

    fun <T : Item> registerItem(id: String, factory: () -> T): Supplier<T> =
        self().registerNoEntry(id, BuiltInRegistries.ITEM, factory)

    fun <T : Block> T.registerBlock(id: String): Supplier<T> = with(self()) {
        registerNoEntry(id, BuiltInRegistries.BLOCK)
    }

    fun <T : Block> registerBlock(id: String, factory: () -> T): Supplier<T> =
        self().registerNoEntry(id, BuiltInRegistries.BLOCK, factory)

    fun <T : CreativeModeTab> T.registerCreativeTab(id: String): Supplier<T> = with(self()) {
        registerNoEntry(id, BuiltInRegistries.CREATIVE_MODE_TAB)
    }

    fun <T : CreativeModeTab> registerCreativeTab(id: String, factory: () -> T): Supplier<T> =
        self().registerNoEntry(id, BuiltInRegistries.CREATIVE_MODE_TAB, factory)

    fun <T : Item> T.registerItemHolder(id: String): RegistryHolder<Item, T> = with(self()) {
        registerHolder(id, BuiltInRegistries.ITEM)
    }

    fun <T : Item> registerItemHolder(id: String, factory: () -> T): RegistryHolder<Item, T> =
        self().registerHolder(id, BuiltInRegistries.ITEM, factory)

    fun <T : Block> T.registerBlockHolder(id: String): RegistryHolder<Block, T> = with(self()) {
        registerHolder(id, BuiltInRegistries.BLOCK)
    }

    fun <T : Block> registerBlockHolder(id: String, factory: () -> T): RegistryHolder<Block, T> =
        self().registerHolder(id, BuiltInRegistries.BLOCK, factory)

    fun <T : CreativeModeTab> T.registerCreativeTabHolder(id: String): RegistryHolder<CreativeModeTab, T> = with(self()) {
        registerHolder(id, BuiltInRegistries.CREATIVE_MODE_TAB)
    }

    fun <T : CreativeModeTab> registerCreativeTabHolder(
        id: String,
        factory: () -> T,
    ): RegistryHolder<CreativeModeTab, T> = self().registerHolder(id, BuiltInRegistries.CREATIVE_MODE_TAB, factory)

    fun <T : MobEffect> T.registerMobEffect(id: String): RegistryHolder<MobEffect, T> = with(self()) {
        registerHolder(id, BuiltInRegistries.MOB_EFFECT)
    }

    fun <T : MobEffect> registerMobEffect(id: String, factory: () -> T): RegistryHolder<MobEffect, T> =
        self().registerHolder(id, BuiltInRegistries.MOB_EFFECT, factory)
}

open class RegistrationHandler(val namespace: String) : RegistrationLookup, RegistryDefaultExtensions {
    private val registrations = mutableListOf<PendingRegistration>()
    private val registrationKeys = mutableSetOf<RegistrationKey>()
    private var state = State.DECLARING

    init {
        require(Identifier.tryBuild(namespace, "entry") != null) { "Invalid registration namespace: $namespace" }
    }

    override fun <R : Any, T : R> T.registerNoEntry(id: String, registry: Registry<R>): Supplier<T> =
        declare(identifier(id), registry) { this }

    override fun <R : Any, T : R> T.registerNoEntry(id: Identifier, registry: Registry<R>): Supplier<T> =
        declare(id, registry) { this }

    override fun <R : Any, T : R> registerNoEntry(
        id: String,
        registry: Registry<R>,
        factory: () -> T,
    ): Supplier<T> = declare(identifier(id), registry, Supplier(factory))

    override fun <R : Any, T : R> registerNoEntry(
        id: Identifier,
        registry: Registry<R>,
        factory: () -> T,
    ): Supplier<T> = declare(id, registry, Supplier(factory))

    override fun <R : Any, T : R> T.registerHolder(id: String, registry: Registry<R>): RegistryHolder<R, T> =
        declareHolder(identifier(id), registry) { this }

    override fun <R : Any, T : R> T.registerHolder(id: Identifier, registry: Registry<R>): RegistryHolder<R, T> =
        declareHolder(id, registry) { this }

    override fun <R : Any, T : R> registerHolder(
        id: String,
        registry: Registry<R>,
        factory: () -> T,
    ): RegistryHolder<R, T> = declareHolder(identifier(id), registry, Supplier(factory))

    override fun <R : Any, T : R> registerHolder(
        id: Identifier,
        registry: Registry<R>,
        factory: () -> T,
    ): RegistryHolder<R, T> = declareHolder(id, registry, Supplier(factory))

    override fun init() {
        val pending = synchronized(this) {
            check(state == State.DECLARING) { "Registration handler '$namespace' has already been initialized" }
            state = State.INITIALIZING
            registrations.toList()
        }

        try {
            val platform = RegistrationPlatform.instance
            pending.forEach { it.submit(platform) }
            synchronized(this) { state = State.INITIALIZED }
        } catch (error: Throwable) {
            synchronized(this) { state = State.FAILED }
            throw error
        }
    }

    override fun self(): RegistrationLookup = this

    private fun identifier(path: String): Identifier {
        require(path.isNotBlank()) { "Registration id cannot be blank" }
        require(':' !in path) { "Registration id must be a path without a namespace: $path" }
        return requireNotNull(Identifier.tryBuild(namespace, path)) { "Invalid registration id: $namespace:$path" }
    }

    private fun <R : Any, T : R> declare(
        id: Identifier,
        registry: Registry<R>,
        factory: Supplier<T>,
    ): Supplier<T> = synchronized(this) {
        validateDeclaration(id, registry)
        val reference = RegistrationReference<T>(id)
        registrations += TypedPendingRegistration(id, registry, factory, reference)
        reference
    }

    private fun <R : Any, T : R> declareHolder(
        id: Identifier,
        registry: Registry<R>,
        factory: Supplier<T>,
    ): RegistryHolder<R, T> = synchronized(this) {
        validateDeclaration(id, registry)
        val reference = HolderRegistrationReference<R, T>(ResourceKey.create(registry.key(), id))
        registrations += TypedPendingHolderRegistration(id, registry, factory, reference)
        reference
    }

    private fun validateDeclaration(id: Identifier, registry: Registry<*>) {
        check(state == State.DECLARING) {
            "Cannot declare $id after registration handler '$namespace' has started"
        }
        val key = RegistrationKey(registry.key().identifier(), id)
        require(registrationKeys.add(key)) {
            "Duplicate registration ${key.entry} in registry ${key.registry}"
        }
    }

    private data class RegistrationKey(val registry: Identifier, val entry: Identifier)

    private enum class State {
        DECLARING,
        INITIALIZING,
        INITIALIZED,
        FAILED,
    }

    private fun interface PendingRegistration {
        fun submit(platform: RegistrationPlatform)
    }

    private class TypedPendingRegistration<R : Any, T : R>(
        private val id: Identifier,
        private val registry: Registry<R>,
        private val factory: Supplier<T>,
        private val reference: RegistrationReference<T>,
    ) : PendingRegistration {
        override fun submit(platform: RegistrationPlatform) {
            reference.bind(platform.register(id, registry, factory))
        }
    }

    private class TypedPendingHolderRegistration<R : Any, T : R>(
        private val id: Identifier,
        private val registry: Registry<R>,
        private val factory: Supplier<T>,
        private val reference: HolderRegistrationReference<R, T>,
    ) : PendingRegistration {
        override fun submit(platform: RegistrationPlatform) {
            reference.bind(platform.registerHolder(id, registry, factory))
        }
    }

    private class RegistrationReference<T>(private val id: Identifier) : Supplier<T> {
        @Volatile
        private var delegate: Supplier<T>? = null

        fun bind(delegate: Supplier<T>) {
            check(this.delegate == null) { "Registration $id already has a platform supplier" }
            this.delegate = delegate
        }

        override fun get(): T = checkNotNull(delegate) {
            "Registration $id is unavailable because its handler has not been initialized"
        }.get()
    }

    private class HolderRegistrationReference<R : Any, T : R>(
        override val key: ResourceKey<R>,
    ) : RegistryHolder<R, T> {
        @Volatile
        private var delegate: PlatformRegistryHolder<R, T>? = null

        override val id: Identifier
            get() = key.identifier()

        override val holder: Holder<R>
            get() = platformHolder().holder

        override val isBound: Boolean
            get() = delegate?.holder?.isBound ?: false

        fun bind(delegate: PlatformRegistryHolder<R, T>) {
            check(this.delegate == null) { "Registration $id already has a platform holder" }
            this.delegate = delegate
        }

        override fun get(): T = platformHolder().supplier.get()

        override fun toString(): String = "RegistryHolder{$key}"

        private fun platformHolder(): PlatformRegistryHolder<R, T> = checkNotNull(delegate) {
            "Registration $id is unavailable because its handler has not been initialized"
        }
    }
}

interface RegistrationPlatform {
    fun <R : Any, T : R> register(
        id: Identifier,
        registry: Registry<R>,
        factory: Supplier<T>,
    ): Supplier<T>

    fun <R : Any, T : R> registerHolder(
        id: Identifier,
        registry: Registry<R>,
        factory: Supplier<T>,
    ): PlatformRegistryHolder<R, T>

    companion object {
        @JvmStatic
        lateinit var instance: RegistrationPlatform
    }
}

interface RegistryHolder<R : Any, T : R> : Supplier<T> {
    val id: Identifier

    val key: ResourceKey<R>

    val holder: Holder<R>

    val isBound: Boolean

    operator fun getValue(thisRef: Any?, property: KProperty<*>): Holder<R> = holder
}

data class PlatformRegistryHolder<R : Any, T : R>(
    val holder: Holder<R>,
    val supplier: Supplier<T>,
)
