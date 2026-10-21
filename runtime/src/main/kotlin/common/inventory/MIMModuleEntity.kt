package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.mim.MIMManager
import com.algorithmlx.ecr.api.mim.MIMModule
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import kotlin.jvm.optionals.getOrNull

abstract class MIMModuleEntity(
    pos: BlockPos, state: BlockState, type: BlockEntityType<*>, size: Int,
    capacity: Int = 0, energySlot: Int = -1
) : MIMDeviceEntity(pos, state, type, size, capacity, energySlot), MIMModule {
    override val blockEntity get() = this
    private var corePos: BlockPos? = null

    fun parent(): MIMManager? {
        val level = level ?: return null
        val pos = corePos ?: return null
        if (!level.isLoaded(pos) || isRemoved || level.getBlockEntity(blockPos) !== this) return null
        return (level.getBlockEntity(pos) as? MIMManager)?.takeIf { !it.blockEntity.isRemoved && it.isLinked(this) }
    }

    override fun attach(manager: MIMManager): Boolean {
        val core = manager.blockEntity
        if (core.level !== level || core.isRemoved || !manager.isLinked(this)) return false
        val previous = corePos
        if (previous != null && previous != core.blockPos && level?.isLoaded(previous) != true) return false
        val existing = parent()
        if (existing != null && existing !== manager) return false
        if (corePos != core.blockPos) {
            corePos = core.blockPos.immutable()
            setChanged()
        }
        return true
    }

    override fun saveAdditional(output: ValueOutput) {
        super.saveAdditional(output)
        corePos?.let { output.store("core", BlockPos.CODEC, it) }
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        corePos = input.read("core", BlockPos.CODEC).getOrNull()
    }
}
