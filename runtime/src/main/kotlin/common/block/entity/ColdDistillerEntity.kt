package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.loadMRUData
import com.algorithmlx.ecr.api.mru.saveMRUData
import com.algorithmlx.ecr.api.mru.storage.IOMRUStorage
import com.algorithmlx.ecr.api.mru.storage.MRUStorageContainer
import com.algorithmlx.ecr.api.block.entity.SynchronizedBlockEntity
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class ColdDistillerEntity(worldPosition: BlockPos, blockState: BlockState): SynchronizedBlockEntity(
    BlockEntityTypeRegistry.coldDistiller.get(), worldPosition, blockState
), MRUDevice {
    var mruGenerationRemainder = 0F
    var destroyTime = 0

    override fun saveAdditional(output: ValueOutput) {
        output.putFloat("mru_fraction", this.mruGenerationRemainder)
        output.putInt("destroy_time", this.destroyTime)
        saveMRUData(output)
        super.saveAdditional(output)
    }

    override fun loadAdditional(input: ValueInput) {
        this.mruGenerationRemainder = input.getFloatOr("mru_fraction", 0F)
        this.destroyTime = input.getIntOr("destroy_time", 0)
        loadMRUData(input)
        super.loadAdditional(input)
    }

    override val mruStorage: IOMRUStorage = MRUStorageContainer(100000, MRUTypeRegistry.radiationUnit) { setChanged() }
    override val balance = MRUBalanceContainer(initialUpperBalance = config.balanceProduced, initialLowerBalance = config.balanceProduced) { setChanged() }
    override val deviceType: MRUDevice.DeviceType = MRUDevice.DeviceType.TRANSLATOR

    companion object {
        private val config get() = ECConfig.current.coldDistillerConfig

        @JvmStatic
        fun onTick(level: Level, pos: BlockPos, entity: ColdDistillerEntity) {
            if (level.isClientSide) return
            entity.balance.setBalance(config.balanceProduced, config.balanceProduced)
            entity.generateMRU(level, pos)

            if (!config.destroyIce.enabled || entity.mruStorage.isFilled) return
            entity.tickDestroy(level, pos)
        }

        private fun ColdDistillerEntity.tickDestroy(level: Level, blockPos: BlockPos) {
            val blocks = radiusIceBlock(level, blockPos, config.iceBlockRadius)
            val positions = blocks.keys

            if (positions.isEmpty()) {
                this.destroyTime = 0
                this.setChanged()
                return
            }

            if (this.destroyTime < config.destroyIce.time) {
                this.destroyTime++
                this.setChanged()
                return
            }

            if (!config.destroyIce.chance.isRolled()) {
                this.destroyTime = 0
                this.setChanged()
                return
            }

            val pos = positions.random()
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)

            this.destroyTime = 0
            this.setChanged()
        }

        private fun ColdDistillerEntity.generateMRU(level: Level, pos: BlockPos) {
            val iceBlocks = radiusIceBlock(level, pos, config.iceBlockRadius).size
            val mruPerSecond = iceBlocksToMruPerSecond(iceBlocks)

            if (mruPerSecond <= 0F || this.mruStorage.isFilled) {
                this.mruGenerationRemainder = 0F
                return
            }

            this.mruGenerationRemainder += mruPerSecond / 20F

            val generateAmount = this.mruGenerationRemainder.toInt()
            if (generateAmount <= 0) return

            val inserted = this.mruStorage.insert(generateAmount)
            if (inserted <= 0 || inserted < generateAmount) this.mruGenerationRemainder = 0F
            else this.mruGenerationRemainder -= inserted.toFloat()

            this.setChanged()
        }

        private fun iceBlocksToMruPerSecond(iceBlocks: Int): Float {
            val maxBlocks = blocksCountPerRadius(config.iceBlockRadius)
            if (config.minIceBlocks == 0 || iceBlocks < config.minIceBlocks) return 0F
            if (iceBlocks >= maxBlocks) return config.maxMruPerSecond.toFloat()

            val progress = (iceBlocks - config.minIceBlocks).toFloat() / (maxBlocks.toFloat() - config.minIceBlocks)
            return config.minMruPerSecond + progress * (config.maxMruPerSecond - config.minMruPerSecond)
        }

        private fun blocksCountPerRadius(radius: Int): Long {
            val side = (radius.toLong() * 2) + 1
            return (side * side * side) - 1
        }

        private fun radiusIceBlock(level: Level, blockPos: BlockPos, radius: Int): Map<BlockPos, BlockState> {
            val map = mutableMapOf<BlockPos, BlockState>()
            val rad = -radius .. radius

            rad.forEach { x -> rad.forEach { y -> rad.forEach z@{ z ->
                val pos = BlockPos(blockPos.x + x, blockPos.y + y, blockPos.z + z)
                val state = level.getBlockState(pos)
                if (!state.`is`(BlockTags.ICE)) return@z
                map[pos] = state
            } } }

            return map.toMap()
        }
    }
}
