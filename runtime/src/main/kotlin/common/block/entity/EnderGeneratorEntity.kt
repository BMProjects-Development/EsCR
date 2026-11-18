package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.common.machine.PoweredMachineEntity
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB

class EnderGeneratorEntity(pos: BlockPos, state: BlockState): PoweredMachineEntity(
    BlockEntityTypeRegistry.enderGenerator.get(), pos, state, 0, ECConfig.current.enderGenerator.capacity, MRUDevice.DeviceType.TRANSLATOR, -1
) {
    override fun serverTick(level: ServerLevel) {
        val config = ECConfig.current.enderGenerator
        initializeBalance(level, config.defaultBalance)
        if (!enabled) return
        val area = AABB(blockPos).inflate(config.radius.toDouble())
        for (entity in level.getEntitiesOfClass(LivingEntity::class.java, area) { it.isAlive && BuiltInRegistries.ENTITY_TYPE.getKey(it.type).toString() in config.entities }) {
            entity.teleportTo(blockPos.x + 0.5, blockPos.y + 1.0, blockPos.z + 0.5)
            if (entity.hurtServer(level, level.damageSources().magic(), config.damage)) mruStorage.insert(config.generation)
        }
    }
}
