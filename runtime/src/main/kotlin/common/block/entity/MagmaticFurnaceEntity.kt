package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.common.block.MagmaticFurnace
import com.algorithmlx.ecr.common.machine.MagmaticRecipes
import com.algorithmlx.ecr.common.machine.PoweredMachineEntity
import com.algorithmlx.ecr.common.machine.ProcessingCycle
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class MagmaticFurnaceEntity(pos: BlockPos, state: BlockState): PoweredMachineEntity(BlockEntityTypeRegistry.magmaticFurnace.get(), pos, state, 3, ECConfig.current.magmatic.capacity) {
    val cycle = ProcessingCycle()

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = slot == INPUT || super.canPlaceItem(slot, stack)
    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, direction: Direction): Boolean = slot == OUTPUT
    override fun getSlotsForFace(direction: Direction): IntArray = when (direction) {
        Direction.DOWN -> intArrayOf(OUTPUT)
        Direction.UP -> intArrayOf(ENERGY, INPUT)
        else -> intArrayOf(INPUT)
    }

    override fun serverTick(level: ServerLevel) {
        super.serverTick(level)
        if (!enabled) return
        val recipe = MagmaticRecipes.find(level, items[INPUT])
        if (recipe == null) {
            if (cycle.clear()) setChanged()
            return
        }
        val config = ECConfig.current.magmatic
        val speed = (blockState.block as MagmaticFurnace).tier.speed
        if (cycle.prepare(items[INPUT], recipe.result, (config.furnaceTicks / speed).coerceAtLeast(1), config.furnaceCost)) setChanged()
        if (!canFit(OUTPUT, cycle.result) || mruStorage.mru < config.furnaceCost) return
        mruStorage.extract(config.furnaceCost)
        if (cycle.advance()) {
            putResult(OUTPUT, cycle.result)
            items[INPUT].shrink(1)
            cycle.clear()
        }
        setChanged()
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        cycle.save(output.child("cycle"))
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        input.child("cycle").ifPresent(cycle::load)
    }

    companion object {
        private const val ENERGY = 0
        private const val INPUT = 1
        private const val OUTPUT = 2
    }
}
