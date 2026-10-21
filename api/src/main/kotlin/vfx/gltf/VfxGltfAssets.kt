package com.algorithmlx.ecr.api.vfx.gltf

import com.algorithmlx.ecr.api.LOGGER
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier

internal object VfxGltfAssets {
    private val models = hashMapOf<Identifier, VfxGltfModel?>()
    private val textures = hashSetOf<Identifier>()

    fun supports(name: String): Boolean = name.endsWith(".gltf", true) || name.endsWith(".glb", true)

    fun get(name: String): VfxGltfModel? {
        val id = Identifier.tryParse(name) ?: return null
        if (id !in models)
            models[id] = runCatching {
                val resources = Minecraft.getInstance().resourceManager
                fun read(resource: Identifier) = resources.getResourceOrThrow(resource).open().use { it.readBytes() }
                VfxGltfLoader(VfxGltfDocument(id, read(id), ::read)).load()
            }.onFailure { LOGGER.error("Unable to load VFX glTF model {}", id, it) }.getOrNull()
        return models[id]
    }

    fun texture(image: VfxGltfImage): Identifier {
        if (image.bytes != null && textures.add(image.id)) {
            val pixels = NativeImage.read(image.bytes.inputStream())
            Minecraft.getInstance().textureManager.register(image.id, DynamicTexture({ "VFX glTF ${image.id}" }, pixels))
        }
        return image.id
    }

    fun clear() {
        models.clear()
        val manager = Minecraft.getInstance().textureManager
        textures.forEach(manager::release)
        textures.clear()
    }
}
