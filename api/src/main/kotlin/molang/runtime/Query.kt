package com.algorithmlx.ecr.api.molang.runtime

import com.algorithmlx.ecr.api.molang.runtime.Math.abs
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.block.entity.BlockEntity
import kotlin.math.sqrt

interface Query {
    val ground_speed: Float get() = 0f
    val is_moving: Boolean get() = false
    val is_sneaking: Boolean get() = false
    val is_sprinting: Boolean get() = false
    val is_jumping: Boolean get() = false
    val velocity_y: Float get() = 0f
    val velocity_x: Float get() = 0f
    val velocity_z: Float get() = 0f

    val vertical_speed: Float get() = 0f
    val health: Float get() = 0f
    val max_health: Float get() = 0f
    val is_flying: Boolean get() = false
    val fall_ticks: Float get() = 0f
    val is_swimming: Boolean get() = false
    val is_in_water: Boolean get() = false
    val is_in_water_or_rain: Boolean get() = false
    val is_sitting: Boolean get() = false
    val is_sleeping: Boolean get() = false
    val is_hurt: Boolean get() = false
    val is_swinging: Boolean get() = false
    val is_alive: Boolean get() = true
    val is_on_ground: Boolean get() = true
    val head_x_rotation: Float get() = 0f
    val head_y_rotation: Float get() = 0f
    val anim_time: Float get() = 0f
    val life_time: Float get() = 0f
    val modified_distance_moved: Float get() = 0f
    val modified_move_speed: Float get() = 0f

    companion object {
        val EMPTY = object : Query {}
        val GLFW_TIME = object : Query {
            override val anim_time: Float
                get() = System.nanoTime() / 1000000000F
        }
    }
}

class BlockEntityQuery(val blockEntity: BlockEntity): Query {
    private val startTime = anim_time

    override val anim_time: Float
        get() = (blockEntity.level?.gameTime ?: 0L) + partialTick
    override val life_time: Float get() = anim_time - startTime
}

class LivingEntityQuery(val entity: LivingEntity) : Query {
    override val ground_speed: Float
        get() = entity.deltaMovement.let { sqrt(it.x * it.x + it.z * it.z).toFloat() * TICKS_PER_SECOND }
    override val is_moving: Boolean get() = abs(ground_speed) >= MOVEMENT_THRESHOLD
    override val is_sneaking: Boolean get() = entity.isShiftKeyDown
    override val is_sprinting: Boolean get() = entity.isSprinting
    override val is_jumping: Boolean get() = !entity.onGround() && entity.deltaMovement.y > 0.0
    override val velocity_x: Float get() = entity.deltaMovement.x.toFloat()
    override val velocity_y: Float get() = entity.deltaMovement.y.toFloat()
    override val velocity_z: Float get() = entity.deltaMovement.z.toFloat()
    override val vertical_speed: Float get() = entity.deltaMovement.y.toFloat() * 20f
    override val health: Float get() = entity.health
    override val max_health: Float get() = entity.maxHealth
    override val is_flying: Boolean get() = entity.isFallFlying
    override val fall_ticks: Float get() = entity.fallFlyingTicks.toFloat()
    override val is_swimming: Boolean get() = entity.isSwimming
    override val is_in_water: Boolean get() = entity.isInWater
    override val is_in_water_or_rain: Boolean get() = entity.isInWaterOrRain
    override val is_sitting: Boolean get() = entity.isPassenger
    override val is_sleeping: Boolean get() = entity.isSleeping
    override val is_hurt: Boolean get() = entity.hurtTime > 0
    override val is_swinging: Boolean get() = entity.isSwinging
    override val is_alive: Boolean get() = entity.isAlive
    override val is_on_ground: Boolean get() = entity.onGround()
    override val head_x_rotation: Float get() = entity.xRot
    override val head_y_rotation: Float get() = entity.yHeadRot
    override val anim_time: Float get() = entity.tickCount + partialTick
    override val life_time: Float get() = entity.tickCount.toFloat()
    override val modified_distance_moved: Float get() = 0f
    override val modified_move_speed: Float get() = ground_speed
}

private val partialTick: Float
    get() = Minecraft.getInstance().deltaTracker.getGameTimeDeltaPartialTick(false)

private const val TICKS_PER_SECOND = 20f
private const val MOVEMENT_THRESHOLD = 0.01f
