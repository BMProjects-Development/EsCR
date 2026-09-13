package com.algorithmlx.ecr.api.block

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.state.BlockBehaviour
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class BlockPropertiesData(
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("destroy_time")
    val destroyTime: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("explosion_resistance")
    val explosionResistance: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val friction: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("speed_factor")
    val speedFactor: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("jump_factor")
    val jumpFactor: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("bounce_restitution")
    val bounceRestitution: Float? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val instabreak: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("no_collision")
    val noCollision: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("no_occlusion")
    val noOcclusion: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("requires_correct_tool_for_drops")
    val requiresCorrectToolForDrops: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("random_ticks")
    val randomTicks: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("dynamic_shape")
    val dynamicShape: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("no_loot_table")
    val noLootTable: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("ignited_by_lava")
    val ignitedByLava: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val liquid: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("force_solid_on")
    val forceSolidOn: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("force_solid_off")
    val forceSolidOff: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val air: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("no_terrain_particles")
    val noTerrainParticles: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val replaceable: Boolean = false,
) {
    init {
        numericValues().forEach { (name, value) ->
            require(value.isFinite()) { "$name must be finite" }
        }
        explosionResistance?.let {
            require(it >= 0F) { "explosion_resistance cannot be negative" }
        }
        require(!forceSolidOn || !forceSolidOff) {
            "force_solid_on and force_solid_off cannot both be true"
        }
    }

    @Suppress("DEPRECATION")
    fun applyTo(properties: BlockBehaviour.Properties): BlockBehaviour.Properties {
        destroyTime?.let(properties::destroyTime)
        explosionResistance?.let(properties::explosionResistance)
        friction?.let(properties::friction)
        speedFactor?.let(properties::speedFactor)
        jumpFactor?.let(properties::jumpFactor)
        bounceRestitution?.let(properties::bounceRestitution)

        if (instabreak) properties.instabreak()
        if (noCollision) properties.noCollision()
        if (noOcclusion) properties.noOcclusion()
        if (requiresCorrectToolForDrops) properties.requiresCorrectToolForDrops()
        if (randomTicks) properties.randomTicks()
        if (dynamicShape) properties.dynamicShape()
        if (noLootTable) properties.noLootTable()
        if (ignitedByLava) properties.ignitedByLava()
        if (liquid) properties.liquid()
        if (forceSolidOn) properties.forceSolidOn()
        if (forceSolidOff) properties.forceSolidOff()
        if (air) properties.air()
        if (noTerrainParticles) properties.noTerrainParticles()
        if (replaceable) properties.replaceable()

        return properties
    }

    fun toProperties(): BlockBehaviour.Properties = applyTo(BlockBehaviour.Properties.of())

    private fun numericValues(): List<Pair<String, Float>> = buildList {
        destroyTime?.let { add("destroy_time" to it) }
        explosionResistance?.let { add("explosion_resistance" to it) }
        friction?.let { add("friction" to it) }
        speedFactor?.let { add("speed_factor" to it) }
        jumpFactor?.let { add("jump_factor" to it) }
        bounceRestitution?.let { add("bounce_restitution" to it) }
    }
}

class JSONBlockProperties internal constructor(
    private val properties: BlockBehaviour.Properties,
) {
    private var loaded = false

    @Synchronized
    fun load(id: Identifier): BlockBehaviour.Properties {
        check(!loaded) { "JSON block properties can only be loaded once" }
        check(id.namespace in allowedNamespaces) {
            "Block properties namespace '${id.namespace}' is not allowed. Call JSONBlockProperties.allowNamespace(\"${id.namespace}\") during initialization"
        }

        val path = resourcePath(id)
        val primaryClassLoader = Thread.currentThread().contextClassLoader
        val fallbackClassLoader = JSONBlockProperties::class.java.classLoader
        val resource = primaryClassLoader?.getResourceAsStream(path)
            ?: fallbackClassLoader.getResourceAsStream(path)
            ?: error("Missing block properties data file: $path")
        val data = resource.bufferedReader(Charsets.UTF_8).use {
            resourceJson.decodeFromString(BlockPropertiesData.serializer(), it.readText())
        }
        return data.applyTo(properties).also { loaded = true }
    }

    companion object {
        private val allowedNamespaces = ConcurrentHashMap.newKeySet<String>()
        private val resourceJson = Json {
            allowComments = true
            allowTrailingComma = true
        }

        @JvmStatic
        fun allowNamespace(namespace: String) {
            require(Identifier.tryBuild(namespace, "block") != null) { "Invalid namespace: $namespace" }
            allowedNamespaces += namespace
        }

        @JvmStatic
        @JvmOverloads
        fun decode(source: String, json: Json = Json): BlockBehaviour.Properties =
            json.decodeFromString(BlockPropertiesData.serializer(), source).toProperties()

        @JvmStatic
        @JvmOverloads
        fun decode(source: JsonObject, json: Json = Json): BlockBehaviour.Properties =
            json.decodeFromJsonElement(BlockPropertiesData.serializer(), source).toProperties()

        @JvmStatic
        fun resourcePath(id: Identifier): String = "data/${id.namespace}/properties/${id.path}.json"
    }
}

fun BlockBehaviour.Properties.json(): JSONBlockProperties = JSONBlockProperties(this)
