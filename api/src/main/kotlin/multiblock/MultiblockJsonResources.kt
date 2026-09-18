package com.algorithmlx.ecr.api.multiblock

import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import java.util.Collections

data class MultiblockJsonResources(
    val multiblocks: Map<Identifier, String> = emptyMap(),
    val assembledMultiblocks: Map<Identifier, String> = emptyMap()
) {
    internal fun immutableCopy() = MultiblockJsonResources(
        Collections.unmodifiableMap(LinkedHashMap(multiblocks)),
        Collections.unmodifiableMap(LinkedHashMap(assembledMultiblocks))
    )

    companion object {
        @JvmField
        val EMPTY = MultiblockJsonResources()

        @JvmField
        val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MultiblockJsonResources> = StreamCodec.of(
            { buffer, value ->
                buffer.writeJsonMap(value.multiblocks)
                buffer.writeJsonMap(value.assembledMultiblocks)
            },
            { buffer ->
                MultiblockJsonResources(
                    buffer.readJsonMap(),
                    buffer.readJsonMap()
                )
            }
        )

        private fun FriendlyByteBuf.writeJsonMap(values: Map<Identifier, String>) {
            require(values.size <= MAX_DEFINITIONS) { "Too many JSON multiblock definitions: ${values.size}" }
            writeVarInt(values.size)
            values.forEach { (id, json) ->
                writeIdentifier(id)
                writeUtf(json, MAX_DEFINITION_SIZE)
            }
        }

        private fun FriendlyByteBuf.readJsonMap(): Map<Identifier, String> {
            val size = readVarInt()
            require(size in 0..MAX_DEFINITIONS) { "Invalid JSON multiblock definition count: $size" }
            return LinkedHashMap<Identifier, String>(size).apply {
                repeat(size) {
                    val id = readIdentifier()
                    require(put(id, readUtf(MAX_DEFINITION_SIZE)) == null) {
                        "Duplicate JSON multiblock definition $id"
                    }
                }
            }
        }

        private const val MAX_DEFINITIONS = 4096
        private const val MAX_DEFINITION_SIZE = 4 * 1024 * 1024
    }
}
