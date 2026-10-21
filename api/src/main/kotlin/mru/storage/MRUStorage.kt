package com.algorithmlx.ecr.api.mru.storage

import com.algorithmlx.ecr.api.mru.MRUType

interface MRUStorage {
    val mru: Int

    val mruCapacity: Int

    val mruType: MRUType

    val isFilled: Boolean get() = mru == mruCapacity

    val hasMRU: Boolean get() = mru > 0

    val isEmpty: Boolean get() = mru == 0

    fun isSameTypes(storage: MRUStorage): Boolean = this.mruType == storage.mruType
}
