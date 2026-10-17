package com.algorithmlx.ecr.api.vfx

import kotlinx.serialization.json.JsonObject
import org.joml.Vector3f

internal class VfxModule(private val node: VfxNodeRuntime, private val properties: JsonObject) {
    val type = properties.string("type").substringAfterLast('/')
    val noiseSeeds = if (type == "noise") IntArray(3) { node.instance.random.nextInt() } else IntArray(0)
    private val id = properties.string("id")
    private val scalars = hashMapOf<Pair<String, Float>, VfxValue>()
    private val vectors = hashMapOf<String, VfxVector>()

    fun sample(key: String, default: Float = 0f): Float =
        node.sample("modules.$id.$key", scalars.getOrPut(key to default) { properties[key].value(default) })

    fun vector(key: String, default: Float = 0f): Vector3f =
        node.sample("modules.$id.$key", vectors.getOrPut(key) { properties[key].valueVector(default) })

    fun number(key: String, default: Float = 0f): Float = node.instance.timeline(node.spec, "modules.$id.$key", 0, properties.number(key, default))

    fun string(key: String, default: String = "") = properties.string(key, default)

    fun bool(key: String, default: Boolean = false) = properties.bool(key, default)
}
