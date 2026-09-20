package com.algorithmlx.ecr.common.block.entity.enrichment

import com.algorithmlx.ecr.api.block.entity.SynchronizedBlockEntity
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.balance.MutableMRUBalance
import com.algorithmlx.ecr.api.mru.storage.IOMRUStorage
import com.algorithmlx.ecr.api.mru.storage.SynchronizedMRUStorageContainer
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class EnrichmentChamberExtractorEntity(
    worldPosition: BlockPos, blockState: BlockState
): SynchronizedBlockEntity(
    BlockEntityTypeRegistry.enrichmentChamberExtractor.get(),
    worldPosition,
    blockState
), MRUDevice, EnrichmentChamber {
    private var controllerPosition: BlockPos? = null
    private val disconnectedBalance = MRUBalanceContainer()
    private val controllerEntity: EnrichmentChamberControllerEntity?
        get() = controllerPosition
            ?.let { level?.getBlockEntity(it) as? EnrichmentChamberControllerEntity }

    override val mruStorage: IOMRUStorage field = SynchronizedMRUStorageContainer(
        MRUTypeRegistry.radiationUnit
    ) {
        controllerEntity?.mruStorage
    }
    override val balance: MutableMRUBalance
        get() = controllerEntity?.balance ?: disconnectedBalance
    override val deviceType: MRUDevice.DeviceType = MRUDevice.DeviceType.TRANSLATOR

    override fun loadAdditional(input: ValueInput) {
        controllerPosition = null
        if (input.getBooleanOr(HAS_CONTROLLER_POSITION, false)) {
            this.controllerPosition = BlockPos(
                input.getIntOr(CONTROLLER_X, 0),
                input.getIntOr(CONTROLLER_Y, 0),
                input.getIntOr(CONTROLLER_Z, 0)
            )
        }

        super.loadAdditional(input)
    }

    override fun saveAdditional(output: ValueOutput) {
        val pos = controllerPosition
        output.putBoolean(HAS_CONTROLLER_POSITION, pos != null)
        if (pos != null) {
            output.putInt(CONTROLLER_X, pos.x)
            output.putInt(CONTROLLER_Y, pos.y)
            output.putInt(CONTROLLER_Z, pos.z)
        }

        super.saveAdditional(output)
    }

    override fun connectToController(controllerPosition: BlockPos) {
        val immutablePosition = controllerPosition.immutable()
        if (this.controllerPosition == immutablePosition) return

        this.controllerPosition = immutablePosition
        setChanged()
    }

    override fun disconnectFromController(controllerPosition: BlockPos) {
        if (this.controllerPosition != controllerPosition) return

        this.controllerPosition = null
        setChanged()
    }

    companion object {
        private const val HAS_CONTROLLER_POSITION = "has_controller"
        private const val CONTROLLER_X = "controller_x"
        private const val CONTROLLER_Y = "controller_y"
        private const val CONTROLLER_Z = "controller_z"
    }
}
