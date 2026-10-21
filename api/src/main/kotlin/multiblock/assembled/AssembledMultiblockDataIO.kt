package com.algorithmlx.ecr.api.multiblock.assembled

import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.nbt.CompoundTag
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import java.util.Optional
import java.util.UUID

object AssembledMultiblockDataIO {
    private const val DATA_TAG = "assembled_multiblock"
    private const val INSTANCE_TAG = "instance"
    private const val DEFINITION_TAG = "definition"
    private const val CONTROLLER_TAG = "controller"
    private const val FACING_TAG = "facing"
    private const val ORIGINAL_TAG = "original"
    private const val FULL_SNAPSHOT_TAG = "full_snapshot"
    private const val PARTS_TAG = "parts"
    private const val POSITION_TAG = "position"
    private const val STATE_TAG = "state"
    private const val BLOCK_ENTITY_TAG = "block_entity"

    @JvmStatic
    fun write(output: ValueOutput, data: AssembledMultiblockPartData?) {
        data?.let { writePartData(output.child(DATA_TAG), it) }
    }

    @JvmStatic
    fun read(input: ValueInput): AssembledMultiblockPartData? =
        input.child(DATA_TAG).flatMap(::readPartData).orElse(null)

    private fun writePartData(output: ValueOutput, data: AssembledMultiblockPartData) {
        output.putString(INSTANCE_TAG, data.instanceId.toString())
        output.store(DEFINITION_TAG, Identifier.CODEC, data.definitionId)
        output.store(CONTROLLER_TAG, BlockPos.CODEC, data.controllerPos)
        output.store(FACING_TAG, Direction.CODEC, data.facing)
        writeOriginal(output.child(ORIGINAL_TAG), data.original)
        data.fullSnapshot?.let { snapshot ->
            writeSnapshot(output.child(FULL_SNAPSHOT_TAG), snapshot)
        }
    }

    private fun readPartData(input: ValueInput): Optional<AssembledMultiblockPartData> = runCatching {
        val instanceId = UUID.fromString(input.getStringOr(INSTANCE_TAG, ""))
        val definitionId = input.read(DEFINITION_TAG, Identifier.CODEC).orElseThrow()
        val controllerPos = input.read(CONTROLLER_TAG, BlockPos.CODEC).orElseThrow()
        val facing = input.read(FACING_TAG, Direction.CODEC).orElseThrow()
        val original = input.child(ORIGINAL_TAG).flatMap(::readOriginal).orElseThrow()
        val fullSnapshot = input.child(FULL_SNAPSHOT_TAG).flatMap(::readSnapshot).orElse(null)

        AssembledMultiblockPartData(
            instanceId,
            definitionId,
            controllerPos,
            facing,
            original,
            fullSnapshot?.takeIf { snapshot ->
                snapshot.instanceId == instanceId &&
                    snapshot.definitionId == definitionId &&
                    snapshot.controllerPos == controllerPos &&
                    snapshot.facing == facing
            }
        )
    }.fold(
        onSuccess = { Optional.of(it) },
        onFailure = { Optional.empty() }
    )

    private fun writeSnapshot(output: ValueOutput, snapshot: AssembledMultiblockSnapshot) {
        output.putString(INSTANCE_TAG, snapshot.instanceId.toString())
        output.store(DEFINITION_TAG, Identifier.CODEC, snapshot.definitionId)
        output.store(CONTROLLER_TAG, BlockPos.CODEC, snapshot.controllerPos)
        output.store(FACING_TAG, Direction.CODEC, snapshot.facing)

        val parts = output.childrenList(PARTS_TAG)
        snapshot.parts.forEach { part -> writeOriginal(parts.addChild(), part) }
    }

    private fun readSnapshot(input: ValueInput): Optional<AssembledMultiblockSnapshot> = runCatching {
        AssembledMultiblockSnapshot(
            UUID.fromString(input.getStringOr(INSTANCE_TAG, "")),
            input.read(DEFINITION_TAG, Identifier.CODEC).orElseThrow(),
            input.read(CONTROLLER_TAG, BlockPos.CODEC).orElseThrow(),
            input.read(FACING_TAG, Direction.CODEC).orElseThrow(),
            input.childrenListOrEmpty(PARTS_TAG).mapNotNull { partInput ->
                readOriginal(partInput).orElse(null)
            }
        )
    }.fold(
        onSuccess = { Optional.of(it) },
        onFailure = { Optional.empty() }
    )

    private fun writeOriginal(output: ValueOutput, original: OriginalBlockSnapshot) {
        output.store(POSITION_TAG, BlockPos.CODEC, original.position)
        output.store(STATE_TAG, BlockState.CODEC, original.state)
        original.blockEntityData?.let { data ->
            output.store(BLOCK_ENTITY_TAG, CompoundTag.CODEC, data)
        }
    }

    private fun readOriginal(input: ValueInput): Optional<OriginalBlockSnapshot> {
        val position = input.read(POSITION_TAG, BlockPos.CODEC).orElse(null)
            ?: return Optional.empty()
        val state = input.read(STATE_TAG, BlockState.CODEC).orElse(null)
            ?: return Optional.empty()
        val blockEntity = input.read(BLOCK_ENTITY_TAG, CompoundTag.CODEC).orElse(null)

        return Optional.of(OriginalBlockSnapshot(position, state, blockEntity))
    }
}
