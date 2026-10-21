package com.algorithmlx.ecr.api.mru.storage

import com.algorithmlx.ecr.api.mru.MRUType
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class SynchronizedMRUStorageContainer(
    private val fallbackType: MRUType,
    private val source: () -> IOMRUStorage?
) : IOMRUStorage {
    private val currentSource: IOMRUStorage?
        get() = source()?.takeUnless { it === this }

    override val mru: Int
        get() = currentSource?.mru ?: 0

    override val mruCapacity: Int
        get() = currentSource?.mruCapacity ?: 0

    override val mruType: MRUType
        get() = currentSource?.mruType ?: fallbackType

    override fun set(amount: Int) {
        currentSource?.set(amount)
    }

    override fun extract(amount: Int): Int = currentSource?.extract(amount) ?: 0

    override fun insert(amount: Int): Int = currentSource?.insert(amount) ?: 0

    override fun save(output: ValueOutput) = Unit

    override fun load(input: ValueInput) = Unit
}
