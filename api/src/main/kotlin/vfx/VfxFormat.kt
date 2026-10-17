package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.LOGGER
import com.algorithmlx.ecr.api.molang.compiler.FloatExpr
import com.algorithmlx.ecr.api.molang.compiler.MolangCompiler
import com.algorithmlx.ecr.api.molang.runtime.Query
import com.algorithmlx.ecr.api.molang.runtime.Variables
import com.algorithmlx.ecr.api.molang.runtime.VariablesMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.floatOrNull
import org.joml.Vector3f
import kotlin.random.Random
import java.lang.System.identityHashCode

class VfxEffect internal constructor(val nodes: List<VfxNode>, val timeline: VfxTimeline) {
    private val indexedNodes = nodes.asSequence().flatMap(VfxNode::walk).associateBy(VfxNode::id)
    internal val tracks = timeline.tracks.associateBy { it.node to it.property }

    fun node(id: String): VfxNode? = indexedNodes[id]
}

class VfxNode internal constructor(
    val type: String,
    val id: String,
    val enabled: Boolean,
    val transform: VfxTransform,
    val children: List<VfxNode>,
    internal val properties: JsonObject
) {
    fun walk(): Sequence<VfxNode> = sequence {
        yield(this@VfxNode)
        children.forEach { yieldAll(it.walk()) }
    }
}

data class VfxTransform(val position: Vector3f, val rotation: Vector3f, val scale: Vector3f)

data class VfxTimeline(val duration: Float, val loop: Boolean, val tracks: List<VfxTrack>)

data class VfxTrack(val node: String, val property: String, val curves: List<VfxTrackCurve>)

data class VfxTrackCurve(val channel: Int, val visible: Boolean, val curve: VfxCurve)

data class VfxKey(
    val time: Float,
    val value: Float,
    val interpolation: String,
    val inTime: Float,
    val inValue: Float,
    val outTime: Float,
    val outValue: Float
)

class VfxCurve(val keys: List<VfxKey>) {
    fun sample(time: Float): Float {
        if (keys.isEmpty()) return 0f
        if (time <= keys.first().time) return keys.first().value
        if (time >= keys.last().time) return keys.last().value
        val end = keys.indexOfFirst { it.time >= time }.coerceAtLeast(1)
        val a = keys[end - 1]
        val b = keys[end]
        val span = b.time - a.time
        if (span <= 0f || a.interpolation == "CONSTANT") return a.value
        if (a.interpolation == "LINEAR") return a.value + (b.value - a.value) * (time - a.time) / span
        val outTime = a.outTime.coerceAtLeast(0f)
        val inTime = (-b.inTime).coerceAtLeast(0f)
        val shrink = if (outTime + inTime > span) span / (outTime + inTime) else 1f
        val x1 = a.time + outTime * shrink
        val x2 = b.time - inTime * shrink
        var low = 0f
        var high = 1f
        repeat(20) {
            val middle = (low + high) * 0.5f
            if (cubic(a.time, x1, x2, b.time, middle) < time) low = middle else high = middle
        }
        return cubic(a.value, a.value + a.outValue * shrink, b.value + b.inValue * shrink, b.value, (low + high) * 0.5f)
    }

    private fun cubic(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        val s = 1f - t
        return s * s * s * a + 3f * s * s * t * b + 3f * s * t * t * c + t * t * t * d
    }
}

class VfxContext(
    val random: Random,
    val data: Map<String, Float> = emptyMap()
) {
    private val values = VariablesMap()
    val variables: Variables = object : Variables {
        private val zero = object : Variables.Variable {
            override fun get() = 0f
            override fun set(value: Float) = Unit
        }

        override fun getOrNull(name: String) = values.getOrNull(name) ?: zero
        override fun getOrPut(name: String, initialValue: Float) = values.getOrPut(name, initialValue)
    }
    var particleAge = 0f
    var particleActive = false
    var particleLifetime = 1f
    var particleRandom = 0f
    var parentRandom = 0f
    var particleSpeed = 0f
    var emitterAge = 0f
    var effectTime = 0f
    var particleCount = 0
    var cameraDistance = 0f
    var progressOverride: Float? = null
    var position = Vector3f()
    var velocity = Vector3f()

    fun prepare() {
        val progress = input("LIFETIME")
        val entries = mapOf(
            "p.age" to particleAge, "p.lifetime" to particleLifetime, "p.progress" to progress,
            "p.t" to progress, "p.random" to particleRandom, "p.parent_random" to parentRandom,
            "p.speed" to particleSpeed, "p.x" to position.x, "p.y" to position.y, "p.z" to position.z,
            "p.vx" to velocity.x, "p.vy" to velocity.y, "p.vz" to velocity.z,
            "e.age" to emitterAge, "e.time" to effectTime, "e.count" to particleCount.toFloat(),
            "n.camera_distance" to cameraDistance
        )
        entries.forEach { (name, value) -> variables[name] = value }
        data.forEach { (name, value) -> variables["d.$name"] = value }
    }

    fun input(name: String): Float = when (name) {
        "PARTICLE_AGE" -> particleAge
        "EMITTER_AGE" -> emitterAge
        "EFFECT_TIME" -> effectTime
        "SPEED" -> particleSpeed
        "RANDOM" -> particleRandom
        "PARENT_RANDOM" -> parentRandom
        else -> progressOverride ?: (particleAge / particleLifetime.coerceAtLeast(0.0001f)).coerceIn(0f, 1f)
    }

    internal fun useEffect(time: Float, duration: Float) {
        particleActive = false
        particleAge = time
        particleLifetime = duration.coerceAtLeast(0.001f)
        particleRandom = 0f
        parentRandom = 0f
        particleSpeed = 0f
        emitterAge = 0f
        effectTime = time
        progressOverride = null
        position = Vector3f()
        velocity = Vector3f()
    }

    internal fun useParticle(particle: VfxParticle, emitter: VfxEmitter?) {
        particleActive = true
        particleAge = particle.age
        particleLifetime = particle.lifetime
        particleRandom = particle.random
        parentRandom = particle.parentRandom
        particleSpeed = particle.velocity.length()
        position = particle.position
        velocity = particle.velocity
        emitterAge = emitter?.age ?: 0f
        particleCount = emitter?.particles?.size ?: 0
        progressOverride = null
    }
}

sealed interface VfxValue {
    fun sample(context: VfxContext): Float

    data class Constant(val value: Float) : VfxValue {
        override fun sample(context: VfxContext) = value
    }

    data class Range(val from: Float, val to: Float) : VfxValue {
        override fun sample(context: VfxContext): Float {
            val unit = if (context.particleActive) {
                var bits = context.particleRandom.toBits() xor identityHashCode(this)
                bits = (bits xor (bits ushr 16)) * 0x7feb352d
                bits = (bits xor (bits ushr 15)) * 0x846ca68b.toInt()
                ((bits xor (bits ushr 16)) ushr 8) / 16777216f
            } else context.random.nextFloat()
            return from + (to - from) * unit
        }
    }

    data class Curve(val curve: VfxCurve, val input: String, val scale: Float) : VfxValue {
        override fun sample(context: VfxContext) = curve.sample(context.input(input)) * scale
    }

    class Expression(private val source: String) : VfxValue {
        private val compiled: FloatExpr = runCatching { MolangCompiler.compileFloat(source) }.getOrElse {
            LOGGER.warn("Unable to compile VFX expression {}", source, it)
            FloatExpr.ZERO
        }
        private var failed = false
        override fun sample(context: VfxContext): Float {
            context.prepare()
            return runCatching { compiled.getFloat(Query.EMPTY, context.variables) }
                .getOrElse {
                    if (!failed) LOGGER.warn("Unable to evaluate VFX expression {}", source, it)
                    failed = true
                    0f
                }
                .takeIf(Float::isFinite) ?: 0f
        }
    }
}

class VfxVector(val x: VfxValue, val y: VfxValue, val z: VfxValue) {
    fun sample(context: VfxContext) = Vector3f(x.sample(context), y.sample(context), z.sample(context))
}

sealed interface VfxColor {
    fun sample(context: VfxContext): FloatArray

    data class Solid(val color: FloatArray) : VfxColor {
        override fun sample(context: VfxContext) = color.copyOf()
    }

    class Gradient(val stops: List<Pair<Float, FloatArray>>, val input: String) : VfxColor {
        override fun sample(context: VfxContext): FloatArray {
            if (stops.isEmpty()) return floatArrayOf(1f, 1f, 1f, 1f)
            val value = context.input(input)
            if (value <= stops.first().first) return stops.first().second.copyOf()
            if (value >= stops.last().first) return stops.last().second.copyOf()
            val index = stops.indexOfFirst { it.first >= value }
            val left = stops[index - 1]
            val right = stops[index]
            val t = (value - left.first) / (right.first - left.first).coerceAtLeast(0.0001f)
            return FloatArray(4) { left.second[it] + (right.second[it] - left.second[it]) * t }
        }
    }

    class Channels(val channels: List<VfxValue>) : VfxColor {
        override fun sample(context: VfxContext) = FloatArray(4) { channels[it].sample(context) }
    }
}

object VfxFormat {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowComments = true
        allowTrailingComma = true
    }

    fun read(text: String): VfxEffect {
        val parsed = json.parseToJsonElement(text)
        require(parsed is JsonObject) { "VFX root must be an object" }
        validateNumbers(parsed, "root")
        val root = parsed
        require(root.number("version", 1f) == 1f) { "Unsupported VFX version" }
        val timeline = root.obj("timeline")
        val effect = VfxEffect(
            root.array("nodes").map { node(it.obj()) },
            VfxTimeline(
                timeline.number("duration", 5f).coerceAtLeast(0f),
                timeline.bool("loop", true),
                timeline.array("tracks").map { track ->
                    val value = track.obj()
                    VfxTrack(value.string("node"), value.string("property"), value.array("curves").map { item ->
                        val curve = item.obj()
                        VfxTrackCurve(curve.number("channel").toInt(), curve.bool("visible", true), parseCurve(curve))
                    })
                }
            )
        )
        val nodes = effect.nodes.asSequence().flatMap(VfxNode::walk).toList()
        require(nodes.all { it.id.isNotBlank() }) { "VFX nodes require non-empty identifiers" }
        require(nodes.map(VfxNode::id).distinct().size == nodes.size) { "Duplicate VFX node identifier" }
        effect.timeline.tracks.forEach { track ->
            require(effect.node(track.node) != null) { "Unknown timeline node: ${track.node}" }
            require(track.curves.all { it.channel in 0..3 }) { "Invalid timeline channel" }
        }
        return effect
    }

    private fun validateNumbers(value: JsonElement, path: String) {
        when (value) {
            is JsonObject -> value.forEach { (key, child) -> validateNumbers(child, "$path.$key") }
            is JsonArray -> value.forEachIndexed { index, child -> validateNumbers(child, "$path[$index]") }
            is JsonPrimitive -> require(value.floatOrNull?.isFinite() != false) { "Non-finite VFX number at $path" }
        }
    }

    private fun node(value: JsonObject): VfxNode {
        val transform = value.obj("transform")
        return VfxNode(
            value.string("type").substringAfterLast('/'),
            value.string("id"),
            value.bool("enabled", true),
            VfxTransform(transform.vector("position"), transform.vector("rotation"), transform.vector("scale", 1f)),
            value.array("children").map { node(it.obj()) },
            value
        )
    }
}

internal fun JsonElement?.obj(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
internal fun JsonObject.obj(key: String): JsonObject = this[key].obj()
internal fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
internal fun JsonObject.string(key: String, fallback: String = "") = (this[key] as? JsonPrimitive)?.content ?: fallback
internal fun JsonObject.number(key: String, fallback: Float = 0f) = (this[key] as? JsonPrimitive)?.floatOrNull ?: fallback
internal fun JsonObject.bool(key: String, fallback: Boolean = false) = (this[key] as? JsonPrimitive)?.booleanOrNull ?: fallback
internal fun JsonObject.vector(key: String, fallback: Float = 0f): Vector3f {
    val value = this[key]
    return when (value) {
        is JsonArray -> Vector3f(value.getOrNull(0).float(fallback), value.getOrNull(1).float(fallback), value.getOrNull(2).float(fallback))
        is JsonObject -> Vector3f(value.number("x", fallback), value.number("y", fallback), value.number("z", fallback))
        else -> Vector3f(fallback, fallback, fallback)
    }
}

internal fun JsonElement?.float(fallback: Float = 0f) = (this as? JsonPrimitive)?.floatOrNull ?: fallback
internal fun JsonElement?.value(fallback: Float = 0f): VfxValue {
    if (this is JsonPrimitive) return VfxValue.Constant(float(fallback))
    val value = obj()
    return when (value.string("type", "const")) {
        "range" -> VfxValue.Range(value.number("from"), value.number("to", 1f))
        "curve" -> VfxValue.Curve(parseCurve(value.obj("curve")), value.string("input", "LIFETIME"), value.number("scale", 1f))
        "expr" -> VfxValue.Expression(value.string("source"))
        else -> VfxValue.Constant(value.number("value", fallback))
    }
}

internal fun JsonElement?.valueVector(fallback: Float = 0f): VfxVector {
    if (this is JsonPrimitive || this is JsonObject && "type" in this) {
        val scalar = value(fallback)
        return VfxVector(scalar, scalar, scalar)
    }
    if (this is JsonArray) return VfxVector(getOrNull(0).value(fallback), getOrNull(1).value(fallback), getOrNull(2).value(fallback))
    val value = obj()
    return VfxVector(value["x"].value(fallback), value["y"].value(fallback), value["z"].value(fallback))
}

internal fun JsonElement?.color(): VfxColor {
    val value = obj()
    return when (value.string("type", "solid")) {
        "gradient" -> VfxColor.Gradient(value.obj("gradient").array("stops").map {
            val stop = it.obj()
            stop.number("position") to stop.obj("color").rgba()
        }.sortedBy { it.first }, value.string("input", "LIFETIME"))
        "channels" -> VfxColor.Channels(listOf("r", "g", "b", "a").map { value[it].value(1f) })
        else -> VfxColor.Solid(value.obj("color").rgba())
    }
}

internal fun JsonObject.rgba() = floatArrayOf(number("r", 1f), number("g", 1f), number("b", 1f), number("a", 1f))

internal fun parseCurve(value: JsonObject): VfxCurve = VfxCurve(value.array("keys").map {
    val key = it.obj()
    VfxKey(
        key.number("time"), key.number("value"), key.string("interpolation", "BEZIER"),
        key.number("in_time"), key.number("in_value"), key.number("out_time"), key.number("out_value")
    )
}.sortedBy { it.time })
