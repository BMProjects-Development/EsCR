package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.LOGGER
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import net.minecraft.server.packs.resources.ResourceManager

internal object VfxShaderLayouts {
    private var layouts: Map<String, JsonObject> = emptyMap()

    fun reload(resources: ResourceManager) {
        layouts = resources.listResources("vfx_compat") { it.path.endsWith(".json") }.mapNotNull { (id, resource) ->
            runCatching {
                val layout = resource.openAsReader().use { Json.parseToJsonElement(it.readText()).obj() }
                layout.string("shader") to layout
            }.onFailure { LOGGER.error("Unable to load VFX shader compatibility {}", id, it) }.getOrNull()
        }.toMap()
    }

    fun material(source: JsonObject): JsonObject {
        val defaults = layouts[source.string("shader")] ?: return source
        return merge(source, defaults)
    }

    internal fun merge(source: JsonObject, defaults: JsonObject): JsonObject {
        val material = JsonObject(defaults.obj("material") + source)
        val authored = material.array("uniforms").associateBy { it.obj().string("name") }
        val declared = defaults.array("uniforms")
        val names = declared.map { it.obj().string("name") }.toSet()
        val fields = declared.map { authored[it.obj().string("name")] ?: it } + authored.filterKeys { it !in names }.values
        return JsonObject(material + ("uniforms" to JsonArray(fields)))
    }
}
