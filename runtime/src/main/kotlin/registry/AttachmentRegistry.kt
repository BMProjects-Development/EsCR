package com.algorithmlx.ecr.registry

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.attachments.AttachmentRegistrationHandler
import com.algorithmlx.ecr.common.components.PlayerMatrixComponent
import com.algorithmlx.ecr.init.ECRModIDs

object AttachmentRegistry : AttachmentRegistrationHandler(ModId) {
    val playerMatrix = registerAttachment(ECRModIDs.PLAYER_MATRIX, PlayerMatrixComponent::createEmpty) {
        persistent(PlayerMatrixComponent.MAP_CODEC)
        copyOnDeath()
    }
}
