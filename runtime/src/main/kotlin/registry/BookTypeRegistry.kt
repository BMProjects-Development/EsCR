package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.registries.ECRegistryKeys
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.api.research.BookType
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.init.ECRModIDs
import net.minecraft.resources.ResourceKey

object BookTypeRegistry: RegistrationHandler(ModId) {
    private val basicKey = key(ECRModIDs.BASIC)
    private val mruKey = key(ECRModIDs.MRU)
    private val engineerKey = key(ECRModIDs.ENGINEER)
    private val hoannaKey = key(ECRModIDs.HOANNA)
    private val shadeKey = key(ECRModIDs.SHADE)

    val basic = register(basicKey, 0)
    val mru = register(mruKey, 1, basicKey)
    val engineer = register(engineerKey, 2, basicKey, mruKey)
    val hoanna = register(hoannaKey, 3, basicKey, mruKey, engineerKey)
    val shade = register(shadeKey, 4, basicKey, mruKey, engineerKey, hoannaKey)

    private fun register(id: ResourceKey<BookType>, order: Int, vararg inherited: ResourceKey<BookType>) =
        registerHolder(id.identifier(), ECRegistries.BOOK_TYPES) {
            BookType(order, inherited.toSet())
        }
    private fun key(id: String) = ResourceKey.create(ECRegistryKeys.BOOK_TYPE_KEY, id.ecRL)
}
