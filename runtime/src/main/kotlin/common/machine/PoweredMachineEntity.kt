package com.algorithmlx.ecr.common.machine

import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.loadMRUData
import com.algorithmlx.ecr.api.mru.processReceive
import com.algorithmlx.ecr.api.mru.saveMRUData
import com.algorithmlx.ecr.api.mru.storage.MRUStorageContainer
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

abstract class PoweredMachineEntity(
    type: BlockEntityType<*>, pos: BlockPos, state: BlockState, size: Int, capacity: Int,
    override val deviceType: MRUDevice.DeviceType = MRUDevice.DeviceType.CONNECTABLE_RECEIVER,
    private val energySlot: Int = 0
): MachineEntity(type, pos, state, size), MRUDevice {
    override var mruStorage = MRUStorageContainer(capacity, MRUTypeRegistry.radiationUnit) { setChanged() }
        protected set
    override val balance = MRUBalanceContainer { setChanged() }
    override val locator: MRUDevice.LocatorData? get() = if (energySlot >= 0) MRUDevice.LocatorData(this, energySlot) else null
    protected var balanceInitialized = false
    protected val enabled: Boolean get() = level?.hasNeighborSignal(blockPos) == false

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = slot == energySlot && stack.item is BoundGem

    override fun serverTick(level: ServerLevel) {
        if (energySlot >= 0) processReceive(level)
    }

    protected fun initializeBalance(level: ServerLevel, configured: Double) {
        if (balanceInitialized) return
        val value = if (configured < 0.0) level.random.nextDouble() * 2.0 else configured
        balance.setBalance(value, value)
        balanceInitialized = true
        setChanged()
    }

    protected fun resizeStorage(capacity: Int) {
        if (capacity == mruStorage.mruCapacity) return
        val amount = mruStorage.mru
        mruStorage = MRUStorageContainer(capacity, MRUTypeRegistry.radiationUnit) { setChanged() }
        mruStorage.set(amount)
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        saveMRUData(output)
        output.putBoolean("balance_initialized", balanceInitialized)
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        loadMRUData(input)
        balanceInitialized = input.getBooleanOr("balance_initialized", false)
    }
}
