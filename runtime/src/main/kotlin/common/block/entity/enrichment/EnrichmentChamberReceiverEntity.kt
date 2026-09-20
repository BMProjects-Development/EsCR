package com.algorithmlx.ecr.common.block.entity.enrichment

import com.algorithmlx.ecr.api.block.entity.SynchronizedContainerBlockEntity
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.balance.MutableMRUBalance
import com.algorithmlx.ecr.api.mru.processReceive
import com.algorithmlx.ecr.api.mru.storage.IOMRUStorage
import com.algorithmlx.ecr.api.mru.storage.SynchronizedMRUStorageContainer
import com.algorithmlx.ecr.common.api.BoundGemHelper
import com.algorithmlx.ecr.common.menu.EnrichmentChamberReceiverMenu
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class EnrichmentChamberReceiverEntity(
    worldPosition: BlockPos,
    blockState: BlockState
): SynchronizedContainerBlockEntity(
    BlockEntityTypeRegistry.enrichmentChamberReceiver.get(),
    worldPosition,
    blockState
), MRUDevice, EnrichmentChamber {
    private var items = NonNullList.withSize(1, ItemStack.EMPTY)
    private var controllerPosition: BlockPos? = null

    override fun loadAdditional(input: ValueInput) {
        ContainerHelper.loadAllItems(input, this.items)

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
        ContainerHelper.saveAllItems(output, this.items)

        val pos = controllerPosition
        output.putBoolean(HAS_CONTROLLER_POSITION, pos != null)
        if (pos != null) {
            output.putInt(CONTROLLER_X, pos.x)
            output.putInt(CONTROLLER_Y, pos.y)
            output.putInt(CONTROLLER_Z, pos.z)
        }

        super.saveAdditional(output)
    }

    override fun getDefaultName(): Component = Component.empty()

    override fun getItems(): NonNullList<ItemStack> = this.items

    override fun setItems(items: NonNullList<ItemStack>) {
        this.items = items
    }

    override fun createMenu(
        containerId: Int,
        inventory: Inventory
    ): AbstractContainerMenu = EnrichmentChamberReceiverMenu(
        containerId, inventory, this,
        ContainerLevelAccess.create(this.level!!, this.blockPos)
    )

    override fun getContainerSize(): Int = this.items.size
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
    override val deviceType: MRUDevice.DeviceType = MRUDevice.DeviceType.RECEIVER
    override val locator: MRUDevice.LocatorData = MRUDevice.LocatorData(this, 0)

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

    fun hasController() = this.controllerPosition != null

    companion object {
        private const val HAS_CONTROLLER_POSITION = "has_controller"
        private const val CONTROLLER_X = "controller_x"
        private const val CONTROLLER_Y = "controller_y"
        private const val CONTROLLER_Z = "controller_z"

        @JvmStatic
        fun onTick(level: Level, be: EnrichmentChamberReceiverEntity) {
            if (level.isClientSide) return
            val item = be.getItem(0)

            if (item.isEmpty) return
            val boundPos = BoundGemHelper.getBoundPos(item) ?: return

            if (level.getBlockEntity(boundPos) is EnrichmentChamberExtractorEntity) return

            be.processReceive(level)
        }
    }
}
