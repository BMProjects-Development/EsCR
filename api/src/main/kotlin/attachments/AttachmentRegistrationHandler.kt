package com.algorithmlx.ecr.api.attachments

import net.minecraft.resources.Identifier

open class AttachmentRegistrationHandler(val namespace: String) {
    private val registrations = mutableListOf<PendingAttachment>()
    private val registrationIds = mutableSetOf<Identifier>()
    private var state = State.DECLARING

    init {
        require(Identifier.tryBuild(namespace, "entry") != null) { "Invalid attachment namespace: $namespace" }
    }

    fun <T : Any> AttachmentBuilder<T>.registerAttachment(id: String): Attachment<T> = synchronized(this@AttachmentRegistrationHandler) {
        check(state == State.DECLARING) {
            "Cannot declare $id after attachment handler '$namespace' has started"
        }

        val identifier = identifier(id)
        require(registrationIds.add(identifier)) { "Duplicate attachment registration $identifier" }

        val attachment = Attachment<T>(identifier)
        registrations += TypedPendingAttachment(identifier, build(), attachment)
        attachment
    }

    fun <T : Any> registerAttachment(
        id: String,
        initializer: () -> T,
        configure: AttachmentBuilder<T>.() -> Unit = {},
    ): Attachment<T> = attachment(initializer, configure).registerAttachment(id)

    fun init() {
        val pending = synchronized(this) {
            check(state == State.DECLARING) { "Attachment handler '$namespace' has already been initialized" }
            state = State.INITIALIZING
            registrations.toList()
        }

        try {
            val platform = AttachmentPlatform.instance
            pending.forEach { it.submit(platform) }
            synchronized(this) { state = State.INITIALIZED }
        } catch (error: Throwable) {
            synchronized(this) { state = State.FAILED }
            throw error
        }
    }

    private fun identifier(path: String): Identifier {
        require(path.isNotBlank()) { "Attachment id cannot be blank" }
        require(':' !in path) { "Attachment id must be a path without a namespace: $path" }
        return requireNotNull(Identifier.tryBuild(namespace, path)) { "Invalid attachment id: $namespace:$path" }
    }

    private enum class State {
        DECLARING,
        INITIALIZING,
        INITIALIZED,
        FAILED,
    }

    private fun interface PendingAttachment {
        fun submit(platform: AttachmentPlatform)
    }

    private class TypedPendingAttachment<T : Any>(
        private val id: Identifier,
        private val specification: AttachmentSpecification<T>,
        private val attachment: Attachment<T>,
    ) : PendingAttachment {
        override fun submit(platform: AttachmentPlatform) {
            attachment.bind(platform.register(id, specification))
        }
    }
}
