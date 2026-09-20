package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.registries.RegistrationHandler
import com.algorithmlx.ecr.common.effects.MRUCorruption
import com.algorithmlx.ecr.common.init.ECRModIDs

object MobEffectRegistry : RegistrationHandler(ModId) {
    val mruCorruption by registerMobEffect(ECRModIDs.MRU_CORRUPTION, ::MRUCorruption)
}
