package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.common.block.NaturalFurnace
import com.algorithmlx.ecr.common.machine.PoweredMachineEntity
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB
import kotlin.jvm.optionals.getOrNull

class NaturalFurnaceEntity(pos: BlockPos, state: BlockState): PoweredMachineEntity(
    BlockEntityTypeRegistry.naturalFurnace.get(), pos, state, 0,
    capacity(state), MRUDevice.DeviceType.TRANSLATOR, -1
) {
    val isUpgraded: Boolean get() = blockState.getValue(NaturalFurnace.IS_UPGRADED)
    var burnTime = 0
        private set
    var generation = 0
        private set
    var burningPosition: BlockPos? = null
        private set
    private var burningState: BlockState? = null

    override fun serverTick(level: ServerLevel) {
        val config = ECConfig.current.naturalFurnace
        resizeStorage(capacity(blockState))
        initializeBalance(level, config.defaultBalance)
        if (!enabled) return
        if (isUpgraded && level.gameTime % config.plantingInterval == 0L) plantSaplings(level)
        val target = burningPosition
        if (target == null) {
            if (!mruStorage.isFilled) findFuel(level)
            return
        }
        if (!level.isLoaded(target)) return
        if (level.getBlockState(target) != burningState || fuel(level.getBlockState(target)) == null) {
            clearFuel()
            return
        }
        mruStorage.insert(generation)
        burnTime--
        if (burnTime <= 0) {
            val state = level.getBlockState(target)
            if (isUpgraded && state.`is`(BlockTags.LEAVES)) {
                Block.getDrops(state, level, target, null).filter { it.`is`(ItemTags.SAPLINGS) }.forEach { Block.popResource(level, target, it) }
            }
            val replacement = if (isUpgraded) Blocks.AIR.defaultBlockState() else Blocks.DEAD_BUSH.defaultBlockState()
            level.setBlock(target, if (replacement.canSurvive(level, target)) replacement else Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)
            clearFuel()
        }
        setChanged()
    }

    private fun findFuel(level: ServerLevel) {
        val config = ECConfig.current.naturalFurnace
        val radius = if (isUpgraded) config.ultraRadius else config.radius
        val x = blockPos.x + level.random.nextInt(radius * 2 + 1) - radius
        val z = blockPos.z + level.random.nextInt(radius * 2 + 1) - radius
        val height = if (isUpgraded) minOf(config.ultraHeight, level.maxY - blockPos.y - 1) else 0
        for (y in height downTo 0) {
            val pos = BlockPos(x, blockPos.y + y, z)
            if (!level.isLoaded(pos)) continue
            val state = level.getBlockState(pos)
            val fuel = fuel(state) ?: continue
            burningPosition = pos
            burningState = state
            burnTime = fuel.first
            generation = fuel.second
            setChanged()
            return
        }
    }

    private fun fuel(state: BlockState): Pair<Int, Int>? {
        val config = ECConfig.current.naturalFurnace
        if (isUpgraded) return when {
            state.`is`(BlockTags.LOGS) -> config.ultra.logTicks to config.ultra.logGeneration
            state.`is`(BlockTags.LEAVES) -> config.ultra.leavesTicks to config.ultra.leavesGeneration
            state.`is`(BlockTags.FLOWERS) -> config.ultra.flowerTicks to config.ultra.flowerGeneration
            isGrass(state) -> config.ultra.grassTicks to config.ultra.grassGeneration
            else -> null
        }
        return when {
            state.`is`(BlockTags.SAPLINGS) -> config.saplingTicks to config.generation
            state.`is`(BlockTags.FLOWERS) -> config.flowerTicks to config.generation
            isGrass(state) -> config.grassTicks to config.generation
            else -> null
        }
    }

    private fun plantSaplings(level: ServerLevel) {
        val radius = ECConfig.current.naturalFurnace.ultraRadius.toDouble()
        val area = AABB(blockPos).inflate(radius, 0.5, radius)
        for (entity in level.getEntitiesOfClass(ItemEntity::class.java, area) { it.isAlive && it.item.`is`(ItemTags.SAPLINGS) }) {
            val item = entity.item.item as? BlockItem ?: continue
            val pos = entity.blockPosition()
            if (!level.isLoaded(pos) || !level.isEmptyBlock(pos)) continue
            val state = item.block.defaultBlockState()
            if (!state.canSurvive(level, pos) || !level.setBlock(pos, state, Block.UPDATE_ALL)) continue
            entity.item.shrink(1)
            if (entity.item.isEmpty) entity.discard()
        }
    }

    private fun clearFuel() {
        burningPosition = null
        burningState = null
        burnTime = 0
        generation = 0
        setChanged()
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        burningPosition?.let { output.store("burning_position", BlockPos.CODEC, it) }
        burningState?.let { output.store("burning_state", BlockState.CODEC, it) }
        output.putInt("burn_time", burnTime)
        output.putInt("generation", generation)
    }

    override fun loadAdditional(input: ValueInput) {
        resizeStorage(capacity(blockState))
        super.loadAdditional(input)
        burningPosition = input.read("burning_position", BlockPos.CODEC).getOrNull()
        burningState = input.read("burning_state", BlockState.CODEC).getOrNull()
        burnTime = input.getIntOr("burn_time", 0).coerceAtLeast(0)
        generation = input.getIntOr("generation", 0).coerceAtLeast(0)
        if (burningPosition == null || burningState == null || burnTime == 0 || generation == 0) clearFuel()
    }

    companion object {
        private fun capacity(state: BlockState): Int = ECConfig.current.naturalFurnace.let { if (state.getValue(NaturalFurnace.IS_UPGRADED)) it.ultraCapacity else it.capacity }
        private fun isGrass(state: BlockState): Boolean = state.`is`(Blocks.SHORT_GRASS) || state.`is`(Blocks.TALL_GRASS) || state.`is`(Blocks.FERN) || state.`is`(Blocks.LARGE_FERN)
    }
}
