package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.api.mim.MIMFilters
import com.algorithmlx.ecr.common.block.MagicalHopper
import com.algorithmlx.ecr.common.machine.MachineEntity
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB

class MagicalHopperEntity(pos: BlockPos, state: BlockState): MachineEntity(BlockEntityTypeRegistry.magicalHopper.get(), pos, state, 1) {
    private var delay = 0

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = MIMFilters.provider(stack) != null

    override fun serverTick(level: ServerLevel) {
        if (level.hasNeighborSignal(blockPos)) return
        if (delay > 0) {
            delay--
            setChanged()
            return
        }
        val config = ECConfig.current.magicalHopper
        delay = config.interval - 1
        val destination = blockPos.relative(blockState.getValue(MagicalHopper.FACING))
        if (!level.isLoaded(destination)) return
        val output = AABB(destination)
        for (entity in level.getEntitiesOfClass(ItemEntity::class.java, AABB(blockPos).inflate(config.radius.toDouble())) { it.isAlive && !it.item.isEmpty }) {
            if (output.intersects(entity.boundingBox) || !MIMFilters.matches(items[0], entity.item)) continue
            entity.teleportTo(destination.x + 0.5, destination.y + 0.5, destination.z + 0.5)
        }
        setChanged()
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        output.putInt("delay", delay)
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        delay = input.getIntOr("delay", 0).coerceIn(0, ECConfig.current.magicalHopper.interval)
    }
}
