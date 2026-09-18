package com.algorithmlx.ecr.api.multiblock.assembled

import com.algorithmlx.ecr.api.LOGGER
import com.algorithmlx.ecr.api.geo.AnimationType
import com.algorithmlx.ecr.api.geo.GeoAnimationNetwork
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.level.BlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.VoxelShape
import java.util.UUID

fun interface AssembledStateFactory {
    fun create(
        part: AssembledMultiblockPart,
        originalState: BlockState,
        controller: Boolean,
        facing: Direction
    ): BlockState
}

sealed interface AssemblyResult {
    data class Success(val snapshot: AssembledMultiblockSnapshot): AssemblyResult

    data class Failure(
        val reason: AssemblyFailureReason,
        val position: BlockPos? = null
    ): AssemblyResult
}

enum class AssemblyFailureReason {
    CLIENT_SIDE,
    INVALID_FACING,
    UNKNOWN_DEFINITION,
    UNLOADED_PART,
    PATTERN_MISMATCH,
    BLOCK_REPLACEMENT_FAILED,
    MISSING_PART_BLOCK_ENTITY,
    PART_DATA_REJECTED
}

object AssembledMultiblocks {
    private val mutations = ThreadLocal.withInitial { hashSetOf<UUID>() }

    @JvmStatic
    fun assemble(
        level: Level,
        definition: AssembledMultiblockDefinition,
        controllerPos: BlockPos,
        facing: Direction,
        stateFactory: AssembledStateFactory
    ): AssemblyResult {
        val effectiveDefinition = MultiblockDefinitions.assembled(definition.id)
        if (effectiveDefinition != null && effectiveDefinition !== definition) {
            return assemble(level, effectiveDefinition, controllerPos, facing, stateFactory)
        }
        if (level.isClientSide) return AssemblyResult.Failure(AssemblyFailureReason.CLIENT_SIDE)
        if (!facing.axis.isHorizontal) return AssemblyResult.Failure(AssemblyFailureReason.INVALID_FACING)

        val resolvedController = when (
            val resolution = resolveController(level, definition, controllerPos, facing)
        ) {
            is ControllerResolution.Found -> resolution.position
            is ControllerResolution.Failed -> return resolution.result
        }
        val positions = definition.worldPositions(resolvedController, facing)

        val originals = positions.map { position -> OriginalBlockSnapshot.capture(level, position) }
        val snapshot = AssembledMultiblockSnapshot(
            UUID.randomUUID(),
            definition.id,
            resolvedController.immutable(),
            facing,
            originals
        )
        val backupPosition = positions.firstOrNull { position -> position != resolvedController }

        return mutate(snapshot.instanceId) {
            var activePosition: BlockPos? = null
            try {
                definition.parts.forEachIndexed { index, part ->
                    val position = positions[index]
                    activePosition = position
                    val isController = position == resolvedController
                    val formedState = stateFactory.create(part, originals[index].state, isController, facing)
                    val stateChanged = level.setBlock(position, formedState, FORMATION_FLAGS)
                    if (!stateChanged && level.getBlockState(position) != formedState) {
                        rollback(level, originals)
                        return@mutate AssemblyResult.Failure(
                            AssemblyFailureReason.BLOCK_REPLACEMENT_FAILED,
                            position
                        )
                    }

                    val partEntity = level.getBlockEntity(position) as? AssembledMultiblockPartEntity
                    if (partEntity == null) {
                        rollback(level, originals)
                        return@mutate AssemblyResult.Failure(
                            AssemblyFailureReason.MISSING_PART_BLOCK_ENTITY,
                            position
                        )
                    }

                    val partData = AssembledMultiblockPartData(
                        snapshot.instanceId,
                        definition.id,
                        snapshot.controllerPos,
                        facing,
                        originals[index],
                        snapshot.takeIf { isController || position == backupPosition }
                    )
                    partEntity.setAssembledMultiblockData(partData)
                    if (partEntity.assembledMultiblockData != partData) {
                        rollback(level, originals)
                        return@mutate AssemblyResult.Failure(
                            AssemblyFailureReason.PART_DATA_REJECTED,
                            position
                        )
                    }
                }

                AssemblyResult.Success(snapshot)
            } catch (_: Exception) {
                rollback(level, originals)
                AssemblyResult.Failure(
                    AssemblyFailureReason.BLOCK_REPLACEMENT_FAILED,
                    activePosition
                )
            }
        }
    }

    private fun resolveController(
        level: Level,
        definition: AssembledMultiblockDefinition,
        assemblyPos: BlockPos,
        facing: Direction
    ): ControllerResolution {
        if (!definition.allowAssemblyFromAnyPart) {
            return validateController(level, definition, assemblyPos, facing)
        }
        if (!level.isLoaded(assemblyPos)) {
            return ControllerResolution.Failed(
                AssemblyResult.Failure(AssemblyFailureReason.UNLOADED_PART, assemblyPos.immutable())
            )
        }

        val state = level.getBlockState(assemblyPos)
        val candidateParts = definition.parts
            .filter { part -> part.matcher.matches(state) }
            .sortedBy { part -> part.offset != BlockPos.ZERO }
        if (candidateParts.isEmpty()) {
            return ControllerResolution.Failed(
                AssemblyResult.Failure(AssemblyFailureReason.PATTERN_MISMATCH, assemblyPos.immutable())
            )
        }

        var firstUnloaded: BlockPos? = null
        var firstMismatch: BlockPos? = null
        candidateParts.forEach { part ->
            val candidate = definition.controllerPosition(assemblyPos, facing, part) ?: return@forEach
            when (val validation = validateController(level, definition, candidate, facing)) {
                is ControllerResolution.Found -> return validation
                is ControllerResolution.Failed -> when (validation.result.reason) {
                    AssemblyFailureReason.UNLOADED_PART -> {
                        if (firstUnloaded == null) firstUnloaded = validation.result.position
                    }
                    AssemblyFailureReason.PATTERN_MISMATCH -> {
                        if (firstMismatch == null) firstMismatch = validation.result.position
                    }
                    else -> Unit
                }
            }
        }

        return if (firstUnloaded != null) {
            ControllerResolution.Failed(
                AssemblyResult.Failure(AssemblyFailureReason.UNLOADED_PART, firstUnloaded)
            )
        } else {
            ControllerResolution.Failed(
                AssemblyResult.Failure(
                    AssemblyFailureReason.PATTERN_MISMATCH,
                    firstMismatch ?: assemblyPos.immutable()
                )
            )
        }
    }

    private fun validateController(
        level: Level,
        definition: AssembledMultiblockDefinition,
        controllerPos: BlockPos,
        facing: Direction
    ): ControllerResolution {
        definition.worldPositions(controllerPos, facing)
            .firstOrNull { position -> !level.isLoaded(position) }
            ?.let { position ->
                return ControllerResolution.Failed(
                    AssemblyResult.Failure(AssemblyFailureReason.UNLOADED_PART, position)
                )
            }
        definition.firstMismatch(level, controllerPos, facing)?.let { position ->
            return ControllerResolution.Failed(
                AssemblyResult.Failure(AssemblyFailureReason.PATTERN_MISMATCH, position)
            )
        }
        return ControllerResolution.Found(controllerPos.immutable())
    }

    private sealed interface ControllerResolution {
        data class Found(val position: BlockPos) : ControllerResolution
        data class Failed(val result: AssemblyResult.Failure) : ControllerResolution
    }

    @JvmStatic
    fun disassemble(level: Level, partPos: BlockPos): OriginalBlockSnapshot? {
        if (level.isClientSide) return null
        val source = partData(level, partPos) ?: return null

        return mutate(source.instanceId) {
            val fullSnapshot = findFullSnapshot(level, source)
            val originals = fullSnapshot?.parts ?: findLoadedOriginals(level, source)
            if (originals.isEmpty()) return@mutate source.original
            var restoredSelected: OriginalBlockSnapshot? = null

            originals.forEach { original ->
                if (!level.isLoaded(original.position)) return@forEach

                val currentEntity = level.getBlockEntity(original.position) as? AssembledMultiblockPartEntity
                val belongsToInstance =
                    currentEntity?.assembledMultiblockData?.instanceId == source.instanceId
                val currentState = level.getBlockState(original.position)

                if (belongsToInstance || currentState.isAir) {
                    val liveControllerData = if (
                        belongsToInstance && currentState.block === original.state.block
                    ) {
                        currentEntity.clearAssembledMultiblockData()
                        (currentEntity as? BlockEntity)?.saveWithFullMetadata(level.registryAccess())
                    } else {
                        null
                    }
                    val restored = original.copy(blockEntityData = liveControllerData ?: original.blockEntityData)
                    restored.restore(level, FORMATION_FLAGS)
                    if (original.position == partPos) restoredSelected = restored
                }
            }

            restoredSelected
                ?: fullSnapshot?.originalAt(partPos)
                ?: originals.firstOrNull { it.position == partPos }
                ?: source.original
        }
    }

    @JvmStatic
    fun controllerBlockEntity(level: Level, partPos: BlockPos): BlockEntity? {
        val data = partData(level, partPos) ?: return null
        return level.getBlockEntity(data.controllerPos)
    }

    @JvmStatic
    fun controllerOriginalState(level: BlockGetter, partPos: BlockPos): BlockState? {
        val source = partData(level, partPos) ?: return null
        val controller = partData(level, source.controllerPos)
        if (controller?.instanceId == source.instanceId) return controller.original.state

        return source.fullSnapshot
            ?.takeIf { snapshot -> snapshot.instanceId == source.instanceId }
            ?.originalAt(source.controllerPos)
            ?.state
    }

    @JvmStatic
    fun formedPartShape(level: BlockGetter, partPos: BlockPos): VoxelShape? {
        val data = partData(level, partPos) ?: return null
        val definition = MultiblockDefinitions.assembled(data.definitionId) ?: return null
        return definition.formedShapeAt(data.controllerPos, data.facing, partPos)
    }

    @JvmStatic
    fun formedSelectionShape(level: BlockGetter, partPos: BlockPos): VoxelShape? {
        val data = partData(level, partPos) ?: return null
        val definition = MultiblockDefinitions.assembled(data.definitionId) ?: return null
        return definition.formedSelectionShapeAt(data.controllerPos, data.facing, partPos)
    }

    @JvmStatic
    fun playAnimation(
        level: Level,
        partPos: BlockPos,
        animation: String,
        type: AnimationType = AnimationType.PLAY_ONCE
    ): Boolean {
        val data = partData(level, partPos)
        if (data == null) {
            LOGGER.error("Cannot play GEO animation '{}': {} is not an assembled multiblock part", animation, partPos)
            return false
        }
        val definition = MultiblockDefinitions.assembled(data.definitionId)
        if (definition?.formedModel == null) {
            LOGGER.error(
                "Cannot play GEO animation '{}': assembled multiblock {} has no formed model",
                animation,
                data.definitionId
            )
            return false
        }

        return GeoAnimationNetwork.play(level, data.controllerPos, animation, type)
    }

    @JvmStatic
    fun stopAnimation(level: Level, partPos: BlockPos): Boolean {
        val data = partData(level, partPos)
        if (data == null) {
            LOGGER.error("Cannot stop GEO animation: {} is not an assembled multiblock part", partPos)
            return false
        }
        return GeoAnimationNetwork.stop(level, data.controllerPos)
    }

    @JvmStatic
    fun tick(level: Level, partPos: BlockPos) {
        if (level.isClientSide || level.gameTime % VALIDATION_INTERVAL != partPos.asLong().mod(VALIDATION_INTERVAL)) {
            return
        }

        val data = partData(level, partPos) ?: return
        if (isMutating(data.instanceId)) return

        if (data.fullSnapshot != null) {
            val invalidLoadedPart = data.fullSnapshot.parts.any { original ->
                level.isLoaded(original.position) &&
                    partData(level, original.position)?.instanceId != data.instanceId
            }
            if (invalidLoadedPart) disassemble(level, partPos)
            return
        }

        if (
            level.isLoaded(data.controllerPos) &&
            partData(level, data.controllerPos)?.instanceId != data.instanceId
        ) {
            disassemble(level, partPos)
        }
    }

    private fun findFullSnapshot(
        level: Level,
        source: AssembledMultiblockPartData
    ): AssembledMultiblockSnapshot? {
        source.fullSnapshot?.let { return it }

        partData(level, source.controllerPos)?.let { controller ->
            if (controller.instanceId == source.instanceId) controller.fullSnapshot?.let { return it }
        }

        val definition = MultiblockDefinitions.assembled(source.definitionId) ?: return null
        return definition.worldPositions(source.controllerPos, source.facing).firstNotNullOfOrNull { position ->
            if (!level.isLoaded(position)) return@firstNotNullOfOrNull null
            partData(level, position)
                ?.takeIf { data -> data.instanceId == source.instanceId }
                ?.fullSnapshot
        }
    }

    private fun findLoadedOriginals(
        level: Level,
        source: AssembledMultiblockPartData
    ): List<OriginalBlockSnapshot> {
        val definition = MultiblockDefinitions.assembled(source.definitionId)
            ?: return listOf(source.original)
        return definition.worldPositions(source.controllerPos, source.facing).mapNotNull { position ->
            if (!level.isLoaded(position)) return@mapNotNull null
            partData(level, position)
                ?.takeIf { data -> data.instanceId == source.instanceId }
                ?.original
        }
    }

    private fun partData(level: BlockGetter, position: BlockPos): AssembledMultiblockPartData? =
        (level.getBlockEntity(position) as? AssembledMultiblockPartEntity)?.assembledMultiblockData

    private fun rollback(level: Level, originals: List<OriginalBlockSnapshot>) {
        originals.forEach { original -> original.restore(level, FORMATION_FLAGS) }
    }

    private fun isMutating(instanceId: UUID): Boolean = instanceId in mutations.get()

    private inline fun <T> mutate(instanceId: UUID, action: () -> T): T {
        val active = mutations.get()
        check(active.add(instanceId)) { "Recursive assembled multiblock mutation for $instanceId" }
        return try {
            action()
        } finally {
            active.remove(instanceId)
            if (active.isEmpty()) mutations.remove()
        }
    }

    private const val VALIDATION_INTERVAL = 20L
    private const val FORMATION_FLAGS = Block.UPDATE_ALL or Block.UPDATE_SUPPRESS_DROPS
}
