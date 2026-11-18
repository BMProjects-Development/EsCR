package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.common.machine.MachineEntity
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState

class MagicalChestEntity(pos: BlockPos, state: BlockState): MachineEntity(BlockEntityTypeRegistry.magicalChest.get(), pos, state, 54)
