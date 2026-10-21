package com.algorithmlx.ecr.common.inventory

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.item.ItemStack
import com.algorithmlx.ecr.api.mim.MIMCraftingPlan
import java.util.Optional
import java.util.UUID
import net.minecraft.core.UUIDUtil
import com.algorithmlx.ecr.api.mim.MIMCraftingState

internal class MIMCraftingRequest(template: ItemStack, var remaining: Int, val id: UUID = UUID.randomUUID()) {
    val template: ItemStack = template.copyWithCount(1)
    var job: MIMCraftingJob? = null
    var plan: MIMCraftingPlan? = null
    var step = 0
    var executions = 0
    var progress: MIMCraftingProgress? = null
    var retryAt = 0L
    var state = MIMCraftingState.IDLE

    fun resetPlan() {
        job = null
        plan = null
        step = 0
        executions = 0
        retryAt = 0
        state = MIMCraftingState.PLANNING
    }

    companion object {
        val CODEC: Codec<MIMCraftingRequest> = RecordCodecBuilder.create { instance ->
            instance.group(
                ItemStack.CODEC.fieldOf("template").forGetter(MIMCraftingRequest::template),
                Codec.intRange(1, Int.MAX_VALUE).fieldOf("remaining").forGetter(MIMCraftingRequest::remaining),
                MIMCraftingProgress.CODEC.optionalFieldOf("work").forGetter { Optional.ofNullable(it.progress) },
                UUIDUtil.CODEC.optionalFieldOf("id").forGetter { Optional.of(it.id) }
            ).apply(instance) { template, remaining, progress, id ->
                MIMCraftingRequest(template, remaining, id.orElseGet(UUID::randomUUID)).also { it.progress = progress.orElse(null) }
            }
        }
    }
}
