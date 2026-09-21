package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.api.block.entity.SynchronizedBlockEntity
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.MRUType
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.storage.IOMRUStorage
import com.algorithmlx.ecr.api.mru.storage.ModifiableMRUStorage
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class CreativeMRUSourceEntity(
    worldPosition: BlockPos,
    blockState: BlockState
) : SynchronizedBlockEntity(
    BlockEntityTypeRegistry.creativeMRUSource.get(),
    worldPosition,
    blockState
), MRUDevice {
    override val mruStorage: IOMRUStorage = ImmutableMRUStorage()
    override val balance = MRUBalanceContainer { setChanged() }
    override val deviceType: MRUDevice.DeviceType = MRUDevice.DeviceType.TRANSLATOR

    private class ImmutableMRUStorage : IOMRUStorage {
        // save/load no need to infinite storage
        override fun save(output: ValueOutput) {}
        override fun load(input: ValueInput) {}
        override fun set(amount: Int) {}
        override fun extract(amount: Int): Int = amount
        override fun insert(amount: Int): Int = amount

        override fun transferTo(receiver: ModifiableMRUStorage, limit: Int): Int = receiver.insert(limit)

        override val mru: Int = 0
        override val mruCapacity: Int = mru
        override val mruType: MRUType = MRUTypeRegistry.radiationUnit
    }
}
