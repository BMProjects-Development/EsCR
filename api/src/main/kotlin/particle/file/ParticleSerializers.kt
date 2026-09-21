package com.algorithmlx.ecr.api.particle.file

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

typealias ListOrSingle<T> = @Serializable(with = ListOrSingleSerializer::class) List<T>

class ListOrSingleSerializer<T>(elementSerializer: KSerializer<T>) :
    JsonTransformingSerializer<List<T>>(ListSerializer(elementSerializer)) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray) element else JsonArray(listOf(element))

    override fun transformSerialize(element: JsonElement): JsonElement =
        (element as? JsonArray)?.singleOrNull() ?: element
}

typealias PairAsList<K, V> = @Serializable(with = PairAsListSerializer::class) Pair<K, V>

class PairAsListSerializer<K, V>(keySerializer: KSerializer<K>, valueSerializer: KSerializer<V>) :
    JsonTransformingSerializer<Pair<K, V>>(PairSerializer(keySerializer, valueSerializer)) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray) {
            JsonObject(mapOf("first" to element[0], "second" to element[1]))
        } else {
            element
        }
}

@Serializable(with = SortedMapSerializer::class)
class SortedMap<K : Comparable<K>, V> private constructor(
    private val sortedEntries: List<Map.Entry<K, V>>,
    private val backing: Map<K, V> = sortedEntries.associate { it.key to it.value }
) : Map<K, V> by backing {
    constructor(map: Map<K, V>) : this(map.entries.sortedBy { it.key })

    fun floorEntry(key: K) = findEntry(key, below = true, inclusive = true)
    fun ceilingEntry(key: K) = findEntry(key, below = false, inclusive = true)
    fun higherEntry(key: K) = findEntry(key, below = false, inclusive = false)
    fun lowestEntry(): Map.Entry<K, V>? = sortedEntries.firstOrNull()

    private fun findEntry(key: K, below: Boolean, inclusive: Boolean): Map.Entry<K, V>? {
        val index = sortedEntries.binarySearchBy(key) { it.key }
        val target = if (index >= 0) {
            when {
                inclusive -> index
                below -> index - 1
                else -> index + 1
            }
        } else {
            val insertionPoint = -index - 1
            if (below) insertionPoint - 1 else insertionPoint
        }
        return sortedEntries.getOrNull(target)
    }
}

class SortedMapSerializer<K : Comparable<K>, V>(
    keySerializer: KSerializer<K>,
    valueSerializer: KSerializer<V>
) : KSerializer<SortedMap<K, V>> {
    private val delegate = MapSerializer(keySerializer, valueSerializer)
    override val descriptor = delegate.descriptor
    override fun serialize(encoder: Encoder, value: SortedMap<K, V>) =
        encoder.encodeSerializableValue(delegate, value)

    override fun deserialize(decoder: Decoder) =
        SortedMap(decoder.decodeSerializableValue(delegate))
}
