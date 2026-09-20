package com.algorithmlx.ecr.neoforge.init.registry

import com.algorithmlx.ecr.api.attachments.AttachmentPlatform
import com.algorithmlx.ecr.api.attachments.AttachmentSpecification
import com.algorithmlx.ecr.api.attachments.PlatformAttachment
import com.algorithmlx.ecr.api.registries.RegistrationPlatform
import net.minecraft.resources.Identifier
import net.neoforged.neoforge.attachment.AttachmentType
import net.neoforged.neoforge.attachment.IAttachmentHolder
import net.neoforged.neoforge.registries.NeoForgeRegistries
import java.util.function.BiPredicate
import java.util.function.Supplier

object NeoForgeAttachmentPlatform : AttachmentPlatform {
    override fun <T : Any> register(
        id: Identifier,
        specification: AttachmentSpecification<T>,
    ): PlatformAttachment<T> {
        val builder = AttachmentType.builder(specification.initializer)
        specification.persistence?.let { builder.serialize(it.mapCodec) }
        if (specification.copyOnDeath) builder.copyOnDeath()
        specification.synchronization?.let { synchronization ->
            builder.sync(
                BiPredicate { holder, player -> synchronization.predicate.test(holder, player) },
                synchronization.codec,
            )
        }
        val reference: Supplier<AttachmentType<T>> = RegistrationPlatform.instance.register(
            id,
            NeoForgeRegistries.ATTACHMENT_TYPES,
            Supplier { builder.build() },
        )
        return NeoForgeAttachment(reference)
    }

    private class NeoForgeAttachment<T : Any>(private val type: Supplier<AttachmentType<T>>) : PlatformAttachment<T> {
        override fun get(holder: Any): T? = holder.target().getExistingDataOrNull(type)

        override fun getOrCreate(holder: Any): T = holder.target().getData(type)

        override fun set(holder: Any, value: T): T? = holder.target().setData(type, value)

        override fun remove(holder: Any): T? = holder.target().removeData(type)

        override fun has(holder: Any): Boolean = holder.target().hasData(type)

        override fun sync(holder: Any) {
            holder.target().syncData(type)
        }

        private fun Any.target(): IAttachmentHolder = this as? IAttachmentHolder
            ?: throw IllegalArgumentException("${this::class.qualifiedName} cannot hold NeoForge attachments")
    }
}
