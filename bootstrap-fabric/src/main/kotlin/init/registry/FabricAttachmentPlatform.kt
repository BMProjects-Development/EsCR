package com.algorithmlx.ecr.fabric.init.registry

import com.algorithmlx.ecr.api.attachments.AttachmentPlatform
import com.algorithmlx.ecr.api.attachments.AttachmentSpecification
import com.algorithmlx.ecr.api.attachments.PlatformAttachment
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget
import net.fabricmc.fabric.api.attachment.v1.AttachmentType
import net.minecraft.resources.Identifier

object FabricAttachmentPlatform : AttachmentPlatform {
    override fun <T : Any> register(
        id: Identifier,
        specification: AttachmentSpecification<T>
    ): PlatformAttachment<T> {
        val type = AttachmentRegistry.create(id) { builder ->
            builder.initializer(specification.initializer)
            specification.persistence?.let { builder.persistent(it.codec) }
            if (specification.copyOnDeath) builder.copyOnDeath()
            specification.synchronization?.let { synchronization ->
                builder.syncWith(
                    synchronization.codec,
                    AttachmentSyncPredicate { holder, player -> synchronization.predicate.test(holder, player) }
                )
            }
        }
        return FabricAttachment(type)
    }

    private class FabricAttachment<T : Any>(private val type: AttachmentType<T>) : PlatformAttachment<T> {
        override fun get(holder: Any): T? = holder.target().getAttached(type)

        override fun getOrCreate(holder: Any): T = holder.target().getAttachedOrCreate(type)

        override fun set(holder: Any, value: T): T? = holder.target().setAttached(type, value)

        override fun remove(holder: Any): T? = holder.target().removeAttached(type)

        override fun has(holder: Any): Boolean = holder.target().hasAttached(type)

        override fun sync(holder: Any) {
            val target = holder.target()
            target.getAttached(type)?.let { target.setAttached(type, it) }
        }

        private fun Any.target(): AttachmentTarget = this as? AttachmentTarget
            ?: throw IllegalArgumentException("${this::class.qualifiedName} cannot hold Fabric attachments")
    }
}
