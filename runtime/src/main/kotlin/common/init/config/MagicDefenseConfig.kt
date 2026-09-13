package com.algorithmlx.ecr.common.init.config

import com.algorithmlx.ecr.common.magic.MagicDefenseContext
import com.algorithmlx.ecr.common.magic.MagicDefenseResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.entity.EntityType
import kotlin.random.Random

// Yo bit... oh, surely swear words arent consideered decent?
// Fuck off with ur so-called of decency. it's my code, so i will do whatever i want with it.
// (c) AlgorithmLX
// Oh fuck, my monitor is dead
// No, it is alive :). HDMI is dead
// Maybe i should scattercomments like that thoughout the code? Why not?

@Serializable
data class MagicDefenseRange(val min: Int, val max: Int) {
    init {
        require(max >= min)
    }

    fun successful(): Boolean = Random.nextInt(max) + 1 >= min
}

// config
@Serializable
data class MagicDefenseEntry(
    val id: String,
    val functions: List<MagicDefenseFunction>
) {
    fun matches(type: EntityType<*>): Boolean = id == BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()

    init {
        require(!id.contains("enderman") && !id.contains("endermite")) {
            "I understand that you might not want to add shields to Enderman/Endermite, " +
                    "but it is not possible. Please respect the mod's lore. That is my only request as a developer :)."
        }
        require(functions.isNotEmpty()) { "Functions cannot be empty." }
    }
}

@Serializable
sealed interface MagicDefenseFunction {
    val priority: Int get() = 100
    val ignoreDamage: Boolean get() = false

    fun apply(context: MagicDefenseContext, result: MagicDefenseResult): MagicDefenseResult
}

@Serializable
@SerialName("ignore")
data object MagicDefenseIgnoreFunction: MagicDefenseFunction {
    override val priority: Int = 0

    override val ignoreDamage: Boolean = true

    override fun apply(
        context: MagicDefenseContext,
        result: MagicDefenseResult
    ): MagicDefenseResult {
        if (result.stopped) return result
        return when (context.enchantmentLevel) {
            0, 1 -> result.stop(0F)
            2 -> result
            else -> result.stop(context.originalDamage * 0.2F)
        }
    }
}

@Serializable
@SerialName("increase")
data class MagicDefenseIncreaseFunction(
    val count: Float
): MagicDefenseFunction {
    override val priority: Int = 200

    override fun apply(
        context: MagicDefenseContext,
        result: MagicDefenseResult
    ): MagicDefenseResult {
        if (result.stopped || (context.hasIgnore && context.enchantmentLevel == 2)) return result
        // without (result.damage - count).coerceAtLeast(0F)
        // yea, i'm a bit mad
        return result.continueWith(result.damage.minus(count).coerceAtLeast(0F))
    }
}

@Serializable
@SerialName("random")
data class MagicDefenseRandomFunction(
    val range: MagicDefenseRange
): MagicDefenseFunction {
    override fun apply(
        context: MagicDefenseContext,
        result: MagicDefenseResult
    ): MagicDefenseResult {
        if (result.stopped) return result

        return if (range.successful()) result
        else result.stop(0F)
    }
}
