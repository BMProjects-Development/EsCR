package com.algorithmlx.ecr.common.machine

import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.api.entity.ShearingPlatform
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantments
import net.minecraft.world.phys.AABB

object AnimalStationLogic {
    fun breed(level: ServerLevel, pos: BlockPos) {
        val config = ECConfig.current.animalStations
        for (entity in level.getEntitiesOfClass(ItemEntity::class.java, AABB(pos).inflate(config.radius.toDouble())) { it.isAlive && !it.item.isEmpty }) {
            val food = entity.item
            val area = AABB(entity.blockPosition()).inflate(config.feedingRadius)
            val animals = level.getEntitiesOfClass(Animal::class.java, area) { it.isAlive && it.age == 0 && it.canFallInLove() && it.isFood(food) }
            for (animal in animals) {
                if (food.isEmpty) break
                animal.setInLove(null)
                food.shrink(1)
            }
            if (food.isEmpty) entity.discard()
        }
    }

    fun shear(level: ServerLevel, pos: BlockPos) {
        val config = ECConfig.current.animalStations
        val shears = ItemStack(Items.SHEARS)
        if (config.fortune > 0) shears.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), config.fortune)
        level.getEntities(null, AABB(pos).inflate(config.radius.toDouble())) { it.isAlive }.forEach { ShearingPlatform.instance.shear(it, level, shears.copy()) }
    }
}
