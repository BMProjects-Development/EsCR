package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.mru.MRUType
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.common.init.ECRModIDs

object MRUTypeRegistry : RegistrationHandler(ModId) {
    private val espeReference = registerNoEntry(ECRModIDs.ESPE, ECRegistries.MRU_TYPE, ::MRUType)
    private val radiationUnitReference = registerNoEntry(ECRModIDs.MRU, ECRegistries.MRU_TYPE, ::MRUType)
    private val ubmruReference = registerNoEntry(ECRModIDs.UBMRU, ECRegistries.MRU_TYPE) {
        MRUType(radiationUnitReference.get(), 10)
    }

    val espe get() = espeReference.get()
    val radiationUnit get() = radiationUnitReference.get()
    val ubmru get() = ubmruReference.get()
}
