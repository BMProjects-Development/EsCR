package com.algorithmlx.ecr.api.mru.storage

interface ModifiableMRUStorage : MRUStorage {
    fun set(amount: Int)

    fun extract(amount: Int): Int

    fun insert(amount: Int): Int

    fun canExtract(max: Int): Boolean = mru - max >= 0

    fun canReceive(receive: Int): Boolean = mru + receive <= mruCapacity

    fun transferTo(
        receiver: ModifiableMRUStorage,
        limit: Int
    ): Int {
        if (receiver === this || limit <= 0 || !isSameTypes(receiver)) return 0

        val freeSpace = (receiver.mruCapacity - receiver.mru).coerceAtLeast(0)
        val requested = minOf(limit, mru, freeSpace)
        if (requested <= 0) return 0

        val extracted = extract(requested)
        if (extracted <= 0) return 0

        val inserted = receiver.insert(extracted)
        if (inserted < extracted) insert(extracted - inserted)
        return inserted
    }
}
