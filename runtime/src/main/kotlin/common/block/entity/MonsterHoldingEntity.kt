package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.common.machine.PoweredMachineEntity
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3

class MonsterHoldingEntity(pos: BlockPos, state: BlockState): PoweredMachineEntity(
    BlockEntityTypeRegistry.monsterHolding.get(), pos, state, 1, ECConfig.current.monsterHolding.capacity
) {
    override fun serverTick(level: ServerLevel) {
        super.serverTick(level)
        if (!enabled) return
        val config = ECConfig.current.monsterHolding
        val center = Vec3.atCenterOf(blockPos)
        for (entity in level.getEntitiesOfClass(LivingEntity::class.java, AABB(blockPos).inflate(config.radius)) { it.isAlive && it !is Player }) {
            if (mruStorage.mru < config.cost) break
            val delta = entity.position().subtract(center)
            val distance = delta.length()
            if (distance >= config.radius || distance < config.radius - config.boundary) continue
            val target = center.add(delta.scale(1.0 / config.attraction))
            entity.teleportTo(target.x, target.y, target.z)
            mruStorage.extract(config.cost)
        }
    }
}
