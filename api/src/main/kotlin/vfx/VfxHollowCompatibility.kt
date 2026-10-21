package com.algorithmlx.ecr.api.vfx

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal object VfxHollowCompatibility {
    private val white = JsonPrimitive("hollowengine:textures/particle/white.png")
    private val circle = JsonPrimitive("hollowengine:textures/particle/circle.png")
    private val fade = Json.parseToJsonElement("""{"type":"gradient","gradient":{"stops":[{"color":{}},{"position":1,"color":{"a":0}}]}}""")
    private val trailWidth = Json.parseToJsonElement("""{"type":"curve","curve":{"keys":[{"value":0.2,"interpolation":"LINEAR"},{"time":1}]}}""")
    private val surfaces = setOf("plane", "cube", "sphere", "cylinder", "trail", "beam")
    private val fixedModuleIds = setOf("noise", "collision", "uv_animation", "velocity_over_lifetime")

    fun node(source: JsonObject): JsonObject {
        val result = source.toMutableMap()
        val originalType = source.string("type")
        val kind = originalType.substringAfterLast('/')
        if (originalType.startsWith("hollowengine:vfx/")) {
            if ("uniforms" in source) result["uniforms"] = JsonArray(source.array("uniforms").filter { it.obj().string("name").isNotBlank() })
            if ("samplers" in source) result["samplers"] = JsonArray(source.array("samplers").filter { it.obj().string("name").isNotBlank() && it.obj().string("texture").isNotBlank() })
            if (kind in surfaces || kind == "model") {
                val material = source.obj("material").toMutableMap()
                if ("uniforms" in material) material["uniforms"] = JsonArray(source.obj("material").array("uniforms").filter { it.obj().string("name").isNotBlank() })
                if ("samplers" in material) material["samplers"] = JsonArray(source.obj("material").array("samplers").filter { it.obj().string("name").isNotBlank() && it.obj().string("texture").isNotBlank() })
                if (kind in surfaces) material.putIfAbsent("texture", if (kind == "plane") circle else white)
                if (kind == "trail" || kind == "beam") material.putIfAbsent("blend", JsonPrimitive("ADDITIVE"))
                if (kind == "model") material.putIfAbsent("lighting", JsonPrimitive(if (source.bool("emissive")) "UNLIT" else "WORLD"))
                result["material"] = JsonObject(material)
            }
            if (kind == "trail") {
                result.putIfAbsent("width", trailWidth)
                result.putIfAbsent("tint", fade)
            }
            if (kind == "beam") result.putIfAbsent("width", JsonPrimitive(0.15f))
            if (kind == "sky") {
                val transform = source.obj("transform").toMutableMap()
                transform.putIfAbsent("position", JsonArray(listOf(JsonPrimitive(0), JsonPrimitive(200), JsonPrimitive(0))))
                result["transform"] = JsonObject(transform)
                result.putIfAbsent("shader", JsonPrimitive("hollowengine:vfx/sky/shockwave"))
            }
            if (kind == "post_effect") result.putIfAbsent("shader", JsonPrimitive("hollowengine:vfx/post/grayscale"))
            if (kind == "emitter")
                result["modules"] = JsonArray(source.array("modules").map { raw ->
                    val module = raw.obj().toMutableMap()
                    val type = raw.obj().string("type").substringAfterLast('/')
                    if (type in fixedModuleIds) module.putIfAbsent("id", JsonPrimitive(type))
                    JsonObject(module)
                })
        }
        if ("material" in result) result["material"] = VfxShaderLayouts.material(result["material"].obj())
        result["children"] = JsonArray(source.array("children").map { node(it.obj()) })
        return JsonObject(result)
    }
}
