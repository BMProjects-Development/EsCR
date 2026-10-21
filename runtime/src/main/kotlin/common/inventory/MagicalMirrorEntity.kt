package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.inventory.InventoryEndpoint
import com.algorithmlx.ecr.api.inventory.InventoryTransfer
import com.algorithmlx.ecr.api.inventory.InventoryAccess
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import kotlin.jvm.optionals.getOrNull

class MagicalMirrorEntity(
    worldPosition: BlockPos,
    blockState: BlockState
) : BlockEntity(BlockEntityTypeRegistry.magicalMirror.get(), worldPosition, blockState) {
    var destination: BlockPos? = null
        private set
    var destinationSide: Direction = Direction.UP
        private set
    var progress: Int = 0
        private set

    fun link(destination: BlockPos, side: Direction): Boolean {
        val level = level ?: return false
        if (level.isClientSide || endpoints(destination, side) == null) return false

        this.destination = destination.immutable()
        destinationSide = side
        progress = 0
        setChanged()
        return true
    }

    fun clearLink() {
        if (destination == null && progress == 0) return
        destination = null
        destinationSide = Direction.UP
        progress = 0
        setChanged()
    }

    fun serverTick() {
        val level = level ?: return
        if (level.isClientSide) return
        val destination = destination ?: return
        val endpoints = endpoints(destination, destinationSide)
        if (endpoints == null || !InventoryTransfer.canMove(endpoints.first, endpoints.second)) {
            resetProgress()
            return
        }

        val ticksRequired = ECConfig.current.magicalMirror.transferTicks.coerceAtLeast(1)
        progress = progress.coerceAtMost(ticksRequired - 1) + 1
        if (progress >= ticksRequired) {
            InventoryTransfer.move(endpoints.first, endpoints.second, 1)
            progress = 0
        }
        setChanged()
    }

    private fun endpoints(destination: BlockPos, side: Direction): Pair<InventoryEndpoint, InventoryEndpoint>? {
        val level = level ?: return null
        val range = ECConfig.current.magicalMirror.range.coerceAtLeast(0.0)
        if (destination == blockPos || destination == blockPos.below() || !(blockPos.distSqr(destination) <= range * range))
            return null
        if (!level.isLoaded(blockPos.below()) || !level.isLoaded(destination)) return null

        val source = InventoryAccess.resolve(level, blockPos.below(), Direction.UP) ?: return null
        val target = InventoryAccess.resolve(level, destination, side) ?: return null
        if (InventoryAccess.overlaps(source, target)) return null
        return source to target
    }

    private fun resetProgress() {
        if (progress == 0) return
        progress = 0
        setChanged()
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        destination?.let {
            output.store("destination", BlockPos.CODEC, it)
            output.store("destination_side", Direction.CODEC, destinationSide)
        }
        output.putInt("progress", progress)
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        destination = input.read("destination", BlockPos.CODEC).getOrNull()
        destinationSide = input.read("destination_side", Direction.CODEC).orElse(Direction.UP)
        progress = if (destination == null) 0 else input.getIntOr("progress", 0).coerceAtLeast(0)
    }
}
