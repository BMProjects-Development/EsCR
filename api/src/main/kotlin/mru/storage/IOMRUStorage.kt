package com.algorithmlx.ecr.api.mru.storage

import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

interface IOMRUStorage: ModifiableMRUStorage {
    fun save(output: ValueOutput)

    fun load(input: ValueInput)
}
