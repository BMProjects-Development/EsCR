package com.algorithmlx.ecr.api.geo.file

import com.algorithmlx.ecr.api.molang.compiler.FloatExpr
import com.algorithmlx.ecr.api.molang.compiler.FloatVec3Expr
import com.algorithmlx.ecr.api.molang.compiler.parseMolangExpression
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class BedrockGeometry(
    val identifier: String,
    val textureWidth: Int,
    val textureHeight: Int,
    val visibleBoundsWidth: Float,
    val visibleBoundsHeight: Float,
    val visibleBoundsOffset: GeoVec3,
    val bones: List<BedrockBone>
)

data class BedrockBone(
    val name: String,
    val parent: String?,
    val pivot: GeoVec3,
    val rotation: GeoVec3,
    val mirror: Boolean,
    val inflate: Float,
    val cubes: List<BedrockCube>
)

data class BedrockCube(
    val origin: GeoVec3,
    val size: GeoVec3,
    val pivot: GeoVec3,
    val rotation: GeoVec3,
    val inflate: Float?,
    val mirror: Boolean?,
    val uv: BedrockUv
)

sealed interface BedrockUv {
    data class Box(val u: Float, val v: Float) : BedrockUv
    data class PerFace(val faces: Map<GeoFaceDirection, Face>) : BedrockUv

    data class Face(
        val u: Float,
        val v: Float,
        val width: Float,
        val height: Float,
        val rotation: Int,
        val materialInstance: String?
    )
}

enum class GeoFaceDirection {
    WEST,
    EAST,
    NORTH,
    SOUTH,
    UP,
    DOWN
}

data class GeoVec3(val x: Float, val y: Float, val z: Float) {
    companion object {
        val ZERO = GeoVec3(0F, 0F, 0F)
    }
}

data class BedrockAnimation(
    val identifier: String,
    val lengthSeconds: Float,
    val loop: BedrockAnimationLoop,
    val overridePreviousAnimation: Boolean,
    val animTimeUpdate: FloatExpr?,
    val blendWeight: FloatExpr,
    val startDelay: FloatExpr,
    val loopDelay: FloatExpr,
    val bones: Map<String, BedrockBoneAnimation>,
    val events: List<BedrockAnimationEvent>
)

enum class BedrockAnimationLoop {
    ONCE,
    LOOP,
    HOLD
}

data class BedrockBoneAnimation(
    val position: BedrockAnimationChannel?,
    val rotation: BedrockAnimationChannel?,
    val scale: BedrockAnimationChannel?
)

data class BedrockAnimationChannel(val keyframes: List<BedrockKeyframe>)

data class BedrockKeyframe(
    val timeSeconds: Float,
    val pre: FloatVec3Expr,
    val post: FloatVec3Expr,
    val interpolation: GeoInterpolation
)

enum class GeoInterpolation {
    LINEAR,
    CATMULL_ROM
}

data class BedrockAnimationEvent(
    val timeSeconds: Float,
    val type: Type,
    val effect: String,
    val locator: String? = null,
    val script: String? = null,
    val bindToActor: Boolean = false
) {
    enum class Type {
        SOUND,
        PARTICLE,
        TIMELINE
    }
}

object BedrockGeoFileParser {
    fun parseGeometry(root: JsonObject): List<BedrockGeometry> {
        val geometries = root["minecraft:geometry"] as? JsonArray
            ?: throw SerializationException("Missing minecraft:geometry array")

        return geometries.map { element ->
            val geometry = element.jsonObject
            val description = geometry.requiredObject("description")
            val identifier = description.requiredString("identifier")
            val textureWidth = description.int("texture_width", 64).coerceAtLeast(1)
            val textureHeight = description.int("texture_height", 64).coerceAtLeast(1)

            BedrockGeometry(
                identifier,
                textureWidth,
                textureHeight,
                description.float("visible_bounds_width", 1F),
                description.float("visible_bounds_height", 1F),
                description.vec3("visible_bounds_offset", GeoVec3.ZERO),
                (geometry["bones"] as? JsonArray).orEmpty().map(::parseBone)
            )
        }
    }

    fun parseAnimations(root: JsonObject): Map<String, BedrockAnimation> {
        val animations = root.requiredObject("animations")
        return animations.mapValues { (identifier, value) -> parseAnimation(identifier, value.jsonObject) }
    }

    private fun parseBone(element: JsonElement): BedrockBone {
        val bone = element.jsonObject
        val pivot = bone.vec3("pivot", GeoVec3.ZERO)
        return BedrockBone(
            bone.requiredString("name"),
            bone.string("parent"),
            pivot,
            bone.vec3("rotation", GeoVec3.ZERO),
            bone.boolean("mirror", false),
            bone.float("inflate", 0F),
            (bone["cubes"] as? JsonArray).orEmpty().map { parseCube(it, pivot) }
        )
    }

    private fun parseCube(element: JsonElement, bonePivot: GeoVec3): BedrockCube {
        val cube = element.jsonObject
        val size = cube.vec3("size", GeoVec3.ZERO)
        return BedrockCube(
            cube.vec3("origin", GeoVec3.ZERO),
            size,
            cube.vec3("pivot", bonePivot),
            cube.vec3("rotation", GeoVec3.ZERO),
            cube["inflate"]?.jsonPrimitive?.floatOrNull,
            cube["mirror"]?.jsonPrimitive?.booleanOrNull,
            parseUv(cube["uv"], size)
        )
    }

    private fun parseUv(element: JsonElement?, size: GeoVec3): BedrockUv = when (element) {
        is JsonArray -> BedrockUv.Box(
            element.getOrNull(0)?.jsonPrimitive?.floatOrNull ?: 0F,
            element.getOrNull(1)?.jsonPrimitive?.floatOrNull ?: 0F
        )
        is JsonObject -> BedrockUv.PerFace(
            GeoFaceDirection.entries.mapNotNull { direction ->
                val face = element[direction.name.lowercase()] as? JsonObject ?: return@mapNotNull null
                val uv = face["uv"] as? JsonArray
                val defaultSize = faceSize(direction, size)
                val uvSize = face["uv_size"] as? JsonArray
                val rotation = face.int("uv_rotation", 0)
                require(rotation % 90 == 0) {
                    "Face '${direction.name.lowercase()}' uv_rotation must be a multiple of 90, got $rotation"
                }
                direction to BedrockUv.Face(
                    uv?.getOrNull(0)?.jsonPrimitive?.floatOrNull ?: 0F,
                    uv?.getOrNull(1)?.jsonPrimitive?.floatOrNull ?: 0F,
                    uvSize?.getOrNull(0)?.jsonPrimitive?.floatOrNull ?: defaultSize.first,
                    uvSize?.getOrNull(1)?.jsonPrimitive?.floatOrNull ?: defaultSize.second,
                    rotation,
                    face.string("material_instance")
                )
            }.toMap()
        )
        else -> BedrockUv.Box(0F, 0F)
    }

    private fun faceSize(direction: GeoFaceDirection, size: GeoVec3): Pair<Float, Float> = when (direction) {
        GeoFaceDirection.WEST, GeoFaceDirection.EAST -> size.z to size.y
        GeoFaceDirection.NORTH, GeoFaceDirection.SOUTH -> size.x to size.y
        GeoFaceDirection.UP, GeoFaceDirection.DOWN -> size.x to size.z
    }

    private fun parseAnimation(identifier: String, animation: JsonObject): BedrockAnimation {
        val loop = when (val value = animation["loop"]) {
            is JsonPrimitive -> when {
                value.booleanOrNull == true -> BedrockAnimationLoop.LOOP
                value.contentOrNull == "hold_on_last_frame" -> BedrockAnimationLoop.HOLD
                else -> BedrockAnimationLoop.ONCE
            }
            else -> BedrockAnimationLoop.ONCE
        }
        val bones = (animation["bones"] as? JsonObject).orEmpty().mapValues { (_, value) ->
            val bone = value.jsonObject
            BedrockBoneAnimation(
                parseChannel(bone["position"]),
                parseChannel(bone["rotation"]),
                parseChannel(bone["scale"])
            )
        }
        val events = buildList {
            parseEvents(animation["sound_effects"], BedrockAnimationEvent.Type.SOUND, this)
            parseEvents(animation["particle_effects"], BedrockAnimationEvent.Type.PARTICLE, this)
            parseEvents(animation["timeline"], BedrockAnimationEvent.Type.TIMELINE, this)
        }.sortedBy(BedrockAnimationEvent::timeSeconds)
        val inferredLength = sequence {
            bones.values.forEach { bone ->
                listOfNotNull(bone.position, bone.rotation, bone.scale).forEach { channel ->
                    channel.keyframes.lastOrNull()?.timeSeconds?.let { yield(it) }
                }
            }
            events.lastOrNull()?.timeSeconds?.let { yield(it) }
        }.maxOrNull() ?: 0F

        return BedrockAnimation(
            identifier,
            animation.float("animation_length", inferredLength),
            loop,
            animation.boolean("override_previous_animation", false),
            animation["anim_time_update"]?.asExpression(),
            animation["blend_weight"]?.asExpression() ?: FloatExpr.ONE,
            animation["start_delay"]?.asExpression() ?: FloatExpr.ZERO,
            animation["loop_delay"]?.asExpression() ?: FloatExpr.ZERO,
            bones,
            events
        )
    }

    private fun parseChannel(element: JsonElement?): BedrockAnimationChannel? = when (element) {
        null, JsonNull -> null
        is JsonArray, is JsonPrimitive -> BedrockAnimationChannel(
            listOf(BedrockKeyframe(0F, element.asVec3Expression(), element.asVec3Expression(), GeoInterpolation.LINEAR))
        )
        is JsonObject -> BedrockAnimationChannel(element.map { (time, value) ->
            val keyframeTime = time.toFloatOrNull()
                ?: throw SerializationException("Invalid animation keyframe time '$time'")
            if (value is JsonObject && ("pre" in value || "post" in value)) {
                val pre = (value["pre"] ?: value["post"] ?: JsonPrimitive(0)).asVec3Expression()
                val post = (value["post"] ?: value["pre"] ?: JsonPrimitive(0)).asVec3Expression()
                BedrockKeyframe(
                    keyframeTime,
                    pre,
                    post,
                    if (value.string("lerp_mode")?.lowercase() == "catmullrom") GeoInterpolation.CATMULL_ROM
                    else GeoInterpolation.LINEAR
                )
            } else {
                val expression = value.asVec3Expression()
                BedrockKeyframe(keyframeTime, expression, expression, GeoInterpolation.LINEAR)
            }
        }.sortedBy(BedrockKeyframe::timeSeconds))
    }

    private fun parseEvents(
        element: JsonElement?,
        type: BedrockAnimationEvent.Type,
        output: MutableList<BedrockAnimationEvent>
    ) {
        val timeline = element as? JsonObject ?: return
        timeline.forEach { (time, value) ->
            val seconds = time.toFloatOrNull() ?: return@forEach
            val values = if (value is JsonArray) value else JsonArray(listOf(value))
            values.forEach { eventValue ->
                when (type) {
                    BedrockAnimationEvent.Type.TIMELINE -> output += BedrockAnimationEvent(
                        seconds,
                        type,
                        eventValue.jsonPrimitive.content
                    )
                    BedrockAnimationEvent.Type.SOUND -> {
                        val event = eventValue as? JsonObject ?: return@forEach
                        output += BedrockAnimationEvent(seconds, type, event.string("effect") ?: return@forEach)
                    }
                    BedrockAnimationEvent.Type.PARTICLE -> {
                        val event = eventValue as? JsonObject ?: return@forEach
                        output += BedrockAnimationEvent(
                            seconds,
                            type,
                            event.string("effect") ?: return@forEach,
                            event.string("locator"),
                            event.string("pre_effect_script"),
                            event.boolean("bind_to_actor", false)
                        )
                    }
                }
            }
        }
    }
}

private fun JsonElement.asExpression(): FloatExpr = when (this) {
    is JsonPrimitive -> parseMolangExpression()
    else -> throw SerializationException("Expected Molang primitive, got $this")
}

private fun JsonElement.asVec3Expression(): FloatVec3Expr = when (this) {
    is JsonArray -> {
        val x = (getOrNull(0) ?: JsonPrimitive(0)).asExpression()
        val y = (getOrNull(1) ?: getOrNull(0) ?: JsonPrimitive(0)).asExpression()
        val z = (getOrNull(2) ?: getOrNull(1) ?: getOrNull(0) ?: JsonPrimitive(0)).asExpression()
        FloatVec3Expr(x, y, z)
    }
    is JsonPrimitive -> {
        val expression = asExpression()
        FloatVec3Expr(expression, expression, expression)
    }
    else -> throw SerializationException("Expected animation vector, got $this")
}

private fun JsonObject.requiredObject(name: String): JsonObject = this[name] as? JsonObject
    ?: throw SerializationException("Missing object '$name'")

private fun JsonObject.requiredString(name: String): String = string(name)
    ?: throw SerializationException("Missing string '$name'")

private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
private fun JsonObject.float(name: String, default: Float): Float = this[name]?.jsonPrimitive?.floatOrNull ?: default
private fun JsonObject.int(name: String, default: Int): Int = this[name]?.jsonPrimitive?.intOrNull ?: default
private fun JsonObject.boolean(name: String, default: Boolean): Boolean =
    this[name]?.jsonPrimitive?.booleanOrNull ?: default

private fun JsonObject.vec3(name: String, default: GeoVec3): GeoVec3 {
    val array = this[name] as? JsonArray ?: return default
    return GeoVec3(
        array.getOrNull(0)?.jsonPrimitive?.floatOrNull ?: default.x,
        array.getOrNull(1)?.jsonPrimitive?.floatOrNull ?: default.y,
        array.getOrNull(2)?.jsonPrimitive?.floatOrNull ?: default.z
    )
}
