package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.LOGGER
import net.minecraft.resources.Identifier
import net.minecraft.client.Minecraft
import net.minecraft.server.packs.resources.ResourceManager
import net.minecraft.server.packs.resources.ResourceManagerReloadListener

object VfxAssets : ResourceManagerReloadListener {
    private val supportedNodes = setOf("group", "emitter", "plane", "cube", "sphere", "cylinder", "model", "trail", "beam", "post_effect", "sky", "camera_shake")
    private val supportedModules = setOf("force", "noise", "collision", "velocity_over_lifetime", "uv_animation")

    @Volatile
    private var effects: Map<Identifier, VfxEffect> = emptyMap()

    val ids: Set<Identifier> get() = effects.keys

    operator fun get(id: Identifier): VfxEffect? = effects[normalize(id)]

    operator fun get(id: String): VfxEffect? = Identifier.tryParse(id)?.let(::get)

    override fun onResourceManagerReload(resourceManager: ResourceManager) {
        VfxShaderLayouts.reload(resourceManager)
        val loaded = linkedMapOf<Identifier, VfxEffect>()
        resourceManager.listResources("vfx") { it.path.endsWith(".vfx") }.forEach { (id, resource) ->
            runCatching { resource.openAsReader().use { VfxFormat.read(it.readText()) } }
                .onSuccess { effect ->
                    loaded[id] = effect
                    val unsupported = effect.nodes.asSequence().flatMap(VfxNode::walk)
                        .filter { it.type !in supportedNodes }
                        .map(VfxNode::type)
                        .distinct()
                        .toList()
                    if (unsupported.isNotEmpty()) LOGGER.warn("VFX {} contains unsupported nodes: {}", id, unsupported.joinToString())
                    val modules = effect.nodes.asSequence().flatMap(VfxNode::walk)
                        .filter { it.type == "emitter" }
                        .flatMap { it.properties.array("modules").asSequence() }
                        .map { it.obj().string("type").substringAfterLast('/') }
                        .filter { it !in supportedModules }
                        .distinct()
                        .toList()
                    if (modules.isNotEmpty()) LOGGER.warn("VFX {} contains unsupported modules: {}", id, modules.joinToString())
                }
                .onFailure { LOGGER.warn("Unable to load VFX {}", id, it) }
        }
        effects = loaded
        Minecraft.getInstance().execute {
            VfxMaterialRenderTypes.clear()
            VfxScenes.clear()
        }
    }

    private fun normalize(id: Identifier): Identifier {
        var path = id.path
        if (!path.startsWith("vfx/")) path = "vfx/$path"
        if (!path.endsWith(".vfx")) path += ".vfx"
        return Identifier.fromNamespaceAndPath(id.namespace, path)
    }
}
