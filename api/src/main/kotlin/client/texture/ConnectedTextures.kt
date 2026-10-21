package com.algorithmlx.ecr.api.client.texture

import net.minecraft.world.level.block.Block
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import java.util.HashMap

object ConnectedTextures {
    private val textures = HashMap<Identifier, ConnectedTexture>()

    @JvmStatic
    fun register(block: Block, texture: ConnectedTexture): ConnectedTexture =
        register(BuiltInRegistries.BLOCK.getKey(block), texture)

    @JvmStatic
    fun register(block: Identifier, texture: ConnectedTexture): ConnectedTexture = synchronized(textures) {
        check(block !in textures) { "Connected texture is already registered for $block" }
        textures[block] = texture
        texture
    }

    @JvmStatic
    fun get(block: Block): ConnectedTexture? = get(BuiltInRegistries.BLOCK.getKey(block))

    @JvmStatic
    fun get(block: Identifier): ConnectedTexture? = synchronized(textures) {
        textures[block]
    }
}
