package com.algorithmlx.ecr.api.multiblock

import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDefinition
import com.algorithmlx.ecr.api.registries.ECRegistries
import net.minecraft.resources.Identifier
import java.util.Collections

object MultiblockDefinitions {
    @Volatile
    private var jsonMultiblocks: Map<Identifier, Multiblock> = emptyMap()

    @Volatile
    private var jsonAssembledMultiblocks: Map<Identifier, AssembledMultiblockDefinition> = emptyMap()

    @Volatile
    private var loadedJsonResources: MultiblockJsonResources = MultiblockJsonResources.EMPTY

    @JvmStatic
    operator fun get(id: Identifier): Multiblock? =
        jsonMultiblocks[id] ?: ECRegistries.MULTIBLOCK.getOptional(id).orElse(null)
            ?.takeUnless(Multiblock::requiresJsonDefinition)

    @JvmStatic
    fun assembled(id: Identifier): AssembledMultiblockDefinition? =
        jsonAssembledMultiblocks[id] ?: ECRegistries.ASSEMBLED_MULTIBLOCK.getOptional(id).orElse(null)
            ?.takeUnless(AssembledMultiblockDefinition::requiresJsonDefinition)

    @JvmStatic
    fun id(definition: Multiblock): Identifier? =
        jsonMultiblocks.entries.firstOrNull { (_, value) -> value === definition }?.key
            ?: ECRegistries.MULTIBLOCK.getKey(definition)

    @JvmStatic
    fun id(definition: AssembledMultiblockDefinition): Identifier? =
        jsonAssembledMultiblocks.entries.firstOrNull { (_, value) -> value === definition }?.key
            ?: ECRegistries.ASSEMBLED_MULTIBLOCK.getKey(definition)

    @JvmStatic
    fun all(): Map<Identifier, Multiblock> = effectiveMap(
        ECRegistries.MULTIBLOCK.keySet(),
        jsonMultiblocks,
        ::get
    )

    @JvmStatic
    fun allAssembled(): Map<Identifier, AssembledMultiblockDefinition> = effectiveMap(
        ECRegistries.ASSEMBLED_MULTIBLOCK.keySet(),
        jsonAssembledMultiblocks,
        ::assembled
    )

    @JvmStatic
    fun hasJsonOverride(id: Identifier): Boolean = id in jsonMultiblocks

    @JvmStatic
    fun hasAssembledJsonOverride(id: Identifier): Boolean = id in jsonAssembledMultiblocks

    @JvmStatic
    fun jsonResources(): MultiblockJsonResources = loadedJsonResources

    @Synchronized
    internal fun installJsonDefinitions(
        multiblocks: Map<Identifier, Multiblock>,
        assembledMultiblocks: Map<Identifier, AssembledMultiblockDefinition>,
        resources: MultiblockJsonResources = loadedJsonResources
    ) {
        jsonMultiblocks = Collections.unmodifiableMap(LinkedHashMap(multiblocks))
        jsonAssembledMultiblocks = Collections.unmodifiableMap(LinkedHashMap(assembledMultiblocks))
        loadedJsonResources = resources.immutableCopy()
    }

    private fun <T : Any> effectiveMap(
        codeIds: Set<Identifier>,
        json: Map<Identifier, T>,
        resolver: (Identifier) -> T?
    ): Map<Identifier, T> {
        val result = linkedMapOf<Identifier, T>()
        (codeIds + json.keys).forEach { id -> resolver(id)?.let { result[id] = it } }
        return Collections.unmodifiableMap(result)
    }
}
