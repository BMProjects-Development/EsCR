package com.algorithmlx.ecr.api.dsl

import com.algorithmlx.ecr.api.ECRAPIScope
import com.algorithmlx.ecr.api.client.research.BookRecipeViewer
import com.algorithmlx.ecr.api.client.research.BookRecipeViewers
import com.algorithmlx.ecr.api.client.texture.ConnectedTexture
import com.algorithmlx.ecr.api.client.texture.ConnectedTextures
import net.minecraft.world.level.block.Block

class ClientRegistrationScope(namespace: String): ECRAPIScope(namespace) {
    fun connectedTexture(block: Block, texture: ConnectedTexture): ConnectedTexture = ConnectedTextures.register(block, texture)
    fun connectedTexture(path: String, texture: ConnectedTexture): ConnectedTexture = ConnectedTextures.register(id(path), texture)
    fun recipeViewer(viewer: BookRecipeViewer) = BookRecipeViewers.register(viewer)
}
