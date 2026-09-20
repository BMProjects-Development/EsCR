package com.algorithmlx.ecr.api.attachments

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerPlayer
import java.util.function.Supplier

class Attachment<T : Any> internal constructor(val id: Identifier) {
    @Volatile
    private var delegate: PlatformAttachment<T>? = null

    fun get(holder: Any): T? = platformAttachment().get(holder)

    fun getOrCreate(holder: Any): T = platformAttachment().getOrCreate(holder)

    fun set(holder: Any, value: T): T? = platformAttachment().set(holder, value)

    fun remove(holder: Any): T? = platformAttachment().remove(holder)

    fun has(holder: Any): Boolean = platformAttachment().has(holder)

    fun sync(holder: Any) {
        platformAttachment().sync(holder)
    }

    fun update(holder: Any, transform: (T) -> T): T {
        val value = transform(getOrCreate(holder))
        set(holder, value)
        return value
    }

    internal fun bind(delegate: PlatformAttachment<T>) {
        check(this.delegate == null) { "Attachment $id already has a platform implementation" }
        this.delegate = delegate
    }

    private fun platformAttachment(): PlatformAttachment<T> = checkNotNull(delegate) {
        "Attachment $id is unavailable because its handler has not been initialized"
    }
}

class AttachmentBuilder<T : Any>(initializer: () -> T) {
    private val initializer = Supplier(initializer)
    private var persistence: AttachmentPersistence<T>? = null
    private var copyOnDeath = false
    private var synchronization: AttachmentSynchronization<T>? = null
    private var built = false

    fun persistent(codec: Codec<T>) = apply {
        setPersistence(AttachmentPersistence(codec, codec.fieldOf("value")))
    }

    fun persistent(codec: MapCodec<T>) = apply {
        setPersistence(AttachmentPersistence(codec.codec(), codec))
    }

    fun copyOnDeath() = apply {
        copyOnDeath = true
    }

    fun syncWith(
        codec: StreamCodec<in RegistryFriendlyByteBuf, T>,
        predicate: AttachmentSyncPredicate = AttachmentSyncPredicate.all(),
    ) = apply {
        check(synchronization == null) { "Attachment synchronization is already configured" }
        synchronization = AttachmentSynchronization(codec, predicate)
    }

    internal fun build(): AttachmentSpecification<T> {
        check(!built) { "Attachment builder has already been built" }
        check(!copyOnDeath || persistence != null) { "copyOnDeath requires a persistent attachment" }
        built = true
        return AttachmentSpecification(initializer, persistence, copyOnDeath, synchronization)
    }

    private fun setPersistence(persistence: AttachmentPersistence<T>) {
        check(this.persistence == null) { "Attachment persistence is already configured" }
        this.persistence = persistence
    }
}

fun <T : Any> attachment(initializer: () -> T, configure: AttachmentBuilder<T>.() -> Unit = {}): AttachmentBuilder<T> =
    AttachmentBuilder(initializer).apply(configure)

fun interface AttachmentSyncPredicate {
    fun test(holder: Any, player: ServerPlayer): Boolean

    companion object {
        fun all(): AttachmentSyncPredicate = AttachmentSyncPredicate { _, _ -> true }

        fun targetOnly(): AttachmentSyncPredicate = AttachmentSyncPredicate { holder, player -> holder === player }

        fun allButTarget(): AttachmentSyncPredicate = AttachmentSyncPredicate { holder, player -> holder !== player }
    }
}

data class AttachmentSpecification<T : Any>(
    val initializer: Supplier<T>,
    val persistence: AttachmentPersistence<T>?,
    val copyOnDeath: Boolean,
    val synchronization: AttachmentSynchronization<T>?,
)

data class AttachmentPersistence<T : Any>(
    val codec: Codec<T>,
    val mapCodec: MapCodec<T>,
)

data class AttachmentSynchronization<T : Any>(
    val codec: StreamCodec<in RegistryFriendlyByteBuf, T>,
    val predicate: AttachmentSyncPredicate,
)

interface PlatformAttachment<T : Any> {
    fun get(holder: Any): T?

    fun getOrCreate(holder: Any): T

    fun set(holder: Any, value: T): T?

    fun remove(holder: Any): T?

    fun has(holder: Any): Boolean

    fun sync(holder: Any)
}

interface AttachmentPlatform {
    fun <T : Any> register(id: Identifier, specification: AttachmentSpecification<T>): PlatformAttachment<T>

    companion object {
        @JvmStatic
        lateinit var instance: AttachmentPlatform
    }
}

fun <T : Any> Any.getAttached(attachment: Attachment<T>): T? = attachment.get(this)

fun <T : Any> Any.getAttachedOrCreate(attachment: Attachment<T>): T = attachment.getOrCreate(this)

fun <T : Any> Any.setAttached(attachment: Attachment<T>, value: T): T? = attachment.set(this, value)

fun <T : Any> Any.removeAttached(attachment: Attachment<T>): T? = attachment.remove(this)

fun Any.hasAttached(attachment: Attachment<*>): Boolean = attachment.has(this)

fun Any.syncAttached(attachment: Attachment<*>) {
    attachment.sync(this)
}
