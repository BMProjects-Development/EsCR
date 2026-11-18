package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.common.machine.MagmaticRecipes
import com.algorithmlx.ecr.common.machine.PoweredMachineEntity
import com.algorithmlx.ecr.common.machine.ProcessingCycle
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.ItemRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class MagmaticSmelteryEntity(pos: BlockPos, state: BlockState): PoweredMachineEntity(BlockEntityTypeRegistry.magmaticSmeltery.get(), pos, state, 8, ECConfig.current.magmatic.capacity) {
    val oreCycle = ProcessingCycle()
    val alloyCycle = ProcessingCycle()
    var lavaAmount = 0
        private set
    val lavaCapacity: Int get() = ECConfig.current.magmatic.lavaCapacity

    fun fillLava(amount: Int, simulate: Boolean = false): Int {
        if (amount <= 0) return 0
        val accepted = minOf(amount, (lavaCapacity - lavaAmount).coerceAtLeast(0))
        if (!simulate && accepted > 0) {
            lavaAmount += accepted
            setChanged()
        }
        return accepted
    }

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = when (slot) {
        LAVA_INPUT -> stack.`is`(Items.LAVA_BUCKET)
        ORE_INPUT -> !MagmaticRecipes.isAlloy(stack)
        ALLOY_INPUT -> MagmaticRecipes.isAlloy(stack)
        else -> super.canPlaceItem(slot, stack)
    }
    override fun getSlotsForFace(direction: Direction): IntArray = when (direction) {
        Direction.UP -> intArrayOf(ENERGY)
        Direction.DOWN -> intArrayOf(BUCKET_OUTPUT, ORE_OUTPUT, ALLOY_OUTPUT, SLAG_OUTPUT)
        else -> intArrayOf(LAVA_INPUT, ORE_INPUT, ALLOY_INPUT)
    }
    override fun canTakeItemThroughFace(slot: Int, stack: ItemStack, direction: Direction): Boolean = slot in OUTPUTS

    override fun serverTick(level: ServerLevel) {
        super.serverTick(level)
        if (!enabled) return
        val bucket = ItemStack(Items.BUCKET)
        if (items[LAVA_INPUT].`is`(Items.LAVA_BUCKET) && canFit(BUCKET_OUTPUT, bucket) && fillLava(BUCKET_VOLUME, true) == BUCKET_VOLUME) {
            fillLava(BUCKET_VOLUME)
            items[LAVA_INPUT].shrink(1)
            putResult(BUCKET_OUTPUT, bucket)
        }
        process(level, oreCycle, ORE_INPUT, ORE_OUTPUT, true)
        process(level, alloyCycle, ALLOY_INPUT, ALLOY_OUTPUT, false)
    }

    private fun process(level: ServerLevel, cycle: ProcessingCycle, input: Int, output: Int, ore: Boolean) {
        val recipe = MagmaticRecipes.find(level, items[input])?.takeIf { it.ore == ore }
        if (recipe == null) {
            if (cycle.clear()) setChanged()
            return
        }
        val config = ECConfig.current.magmatic
        val cost = if (ore) config.oreCost else config.alloyCost
        val ticks = if (ore) config.oreTicks else config.alloyTicks
        if (cycle.prepare(items[input], recipe.result, ticks, cost) {
            if (ore && recipe.result.count <= recipe.result.maxStackSize / 2 && level.random.nextFloat() < config.quadruplingChance) 2 else 1
        }) setChanged()
        val slag = ItemStack(ItemRegistry.magicalSlag.get())
        if (ore && lavaAmount <= 0 || mruStorage.mru < cost || !canFit(output, cycle.result) || !canFit(SLAG_OUTPUT, slag)) return
        mruStorage.extract(cost)
        if (ore && level.random.nextFloat() < config.lavaConsumptionChance) lavaAmount--
        if (cycle.advance()) {
            putResult(output, cycle.result)
            putResult(SLAG_OUTPUT, slag)
            items[input].shrink(1)
            cycle.clear()
        }
        setChanged()
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        output.putInt("lava", lavaAmount)
        oreCycle.save(output.child("ore_cycle"))
        alloyCycle.save(output.child("alloy_cycle"))
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        lavaAmount = input.getIntOr("lava", 0).coerceAtLeast(0)
        input.child("ore_cycle").ifPresent(oreCycle::load)
        input.child("alloy_cycle").ifPresent(alloyCycle::load)
    }

    companion object {
        private const val ENERGY = 0
        private const val LAVA_INPUT = 1
        private const val BUCKET_OUTPUT = 2
        private const val ORE_INPUT = 3
        private const val ORE_OUTPUT = 4
        private const val ALLOY_INPUT = 5
        private const val ALLOY_OUTPUT = 6
        private const val SLAG_OUTPUT = 7
        private const val BUCKET_VOLUME = 1000
        private val OUTPUTS = intArrayOf(BUCKET_OUTPUT, ORE_OUTPUT, ALLOY_OUTPUT, SLAG_OUTPUT)
    }
}
