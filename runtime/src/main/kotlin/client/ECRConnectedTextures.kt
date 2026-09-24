package com.algorithmlx.ecr.client

import com.algorithmlx.ecr.api.client.texture.ConnectedTexture
import com.algorithmlx.ecr.api.client.texture.ConnectedTextures
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.init.ECRModIDs

object ECRConnectedTextures {
    fun init() {
        register(ECRModIDs.DEMONIC_PLATING)
        register(ECRModIDs.FORTIFIED_STONE)
        register(ECRModIDs.MITHRILINE_PLATING)
        register(ECRModIDs.MAGIC_PLATING)
        register(ECRModIDs.PALE_PLATING)
        register(ECRModIDs.FORTIFIED_GLASS, 16)
    }

    private fun register(block: String, texSize: Int = 20) {
        ConnectedTextures.register(
            block = block.ecRL,
            texture = ConnectedTexture.fromMap(
                source = "block/$block/base".ecRL,
                map = "block/$block/map".ecRL,
                textureSize = texSize
            )
        )
    }
}
