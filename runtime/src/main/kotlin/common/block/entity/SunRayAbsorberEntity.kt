package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.api.block.entity.SynchronizedBlockEntity
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.loadMRUData
import com.algorithmlx.ecr.api.mru.saveMRUData
import com.algorithmlx.ecr.api.mru.storage.IOMRUStorage
import com.algorithmlx.ecr.api.mru.storage.MRUStorageContainer
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import com.algorithmlx.ecr.registry.MultiblockRegistry
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class SunRayAbsorberEntity(
    worldPosition: BlockPos,
    blockState: BlockState
) : SynchronizedBlockEntity(
    BlockEntityTypeRegistry.sunAbsorber.get(),
    worldPosition,
    blockState
), MRUDevice {
    var beamTicks: Int = 0
        private set

    var structureValid: Boolean = false
        private set

    override val mruStorage: IOMRUStorage = MRUStorageContainer(
        config.capacity,
        MRUTypeRegistry.radiationUnit
    ) { setChanged() }

    override val balance = MRUBalanceContainer(
        initialUpperBalance = config.balanceProduced,
        initialLowerBalance = config.balanceProduced
    ) { setChanged() }

    override val deviceType: MRUDevice.DeviceType = MRUDevice.DeviceType.TRANSLATOR

    override fun saveAdditional(output: ValueOutput) {
        saveMRUData(output)
        super.saveAdditional(output)
    }

    override fun loadAdditional(input: ValueInput) {
        loadMRUData(input)
        super.loadAdditional(input)
    }

    companion object {
        private val config get() = ECConfig.current.sunRayAbsorber
        private val controllerPosition = BlockPos(3, 1, 3)
        private const val PRISM_HEIGHT = 7
        private const val DAY_LENGTH = 24000L

        @JvmStatic
        fun onTick(level: Level, blockPos: BlockPos, blockEntity: SunRayAbsorberEntity) {
            if (level.isClientSide) return

            blockEntity.balance.setBalance(config.balanceProduced, config.balanceProduced)

            val structureValid = MultiblockRegistry.sunAbsorber.findPlacement(
                level,
                blockPos,
                controllerPosition
            ) != null

            if (blockEntity.structureValid != structureValid) {
                blockEntity.structureValid = structureValid
                blockEntity.setChanged()
            }

            if (!structureValid) {
                blockEntity.beamTicks = 0
                return
            }

            val prismPos = blockPos.above(PRISM_HEIGHT)
            val dayTime = level.overworldClockTime % DAY_LENGTH
            val skyAvailable = !config.requiresUnobstructedSky || level.canSeeSky(prismPos)
            val midday = !config.requiresMidday || dayTime in config.middayStart..config.middayEnd
            val weatherClear = config.ignoreRain || !level.isRaining

            if (!skyAvailable || !midday || !weatherClear) {
                blockEntity.beamTicks = 0
                return
            }

            if (level.random.nextDouble() <= config.beamChance) {
                blockEntity.beamTicks = config.beamDuration
            }

            if (blockEntity.beamTicks <= 0) return
            blockEntity.beamTicks--

            if (level.hasNeighborSignal(blockPos) || blockEntity.mruStorage.isFilled) return

            blockEntity.mruStorage.insert(config.generationPerTick)
        }
    }
}
