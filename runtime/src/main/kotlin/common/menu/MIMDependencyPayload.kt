package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.mim.MIMCraftingPlan
import com.algorithmlx.ecr.api.mim.MIMDependencyStatus
import com.algorithmlx.ecr.api.network.Network
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.common.inventory.MIMCraftingCosts
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack

data class MIMDependencyRequest(
    val containerId: Int, val requestId: Int, val target: ItemStack, val batches: Int
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MIMDependencyRequest>("mim_dependency_request".ecRL)
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MIMDependencyRequest> = StreamCodec.of(
            { buffer, value ->
                buffer.writeVarInt(value.containerId); buffer.writeVarInt(value.requestId)
                ItemStack.STREAM_CODEC.encode(buffer, value.target); buffer.writeVarInt(value.batches)
            },
            { buffer -> MIMDependencyRequest(buffer.readVarInt(), buffer.readVarInt(), ItemStack.STREAM_CODEC.decode(buffer), buffer.readVarInt()) }
        )

        fun handle(player: ServerPlayer, request: MIMDependencyRequest) {
            val menu = player.containerMenu as? MIMMenu ?: return
            if (menu.containerId != request.containerId) return
            val plan = menu.dependencyPlan(player, request.target, request.batches) ?: return
            Network.sendTo(player, MIMDependencyResponse.from(request.containerId, request.requestId, plan))
        }
    }
}

data class MIMDependencyEntry(
    val parent: Int, val stack: ItemStack, val amount: Long, val stored: Long, val crafts: Int, val status: MIMDependencyStatus
)

data class MIMDependencyResponse(
    val containerId: Int, val requestId: Int, val complete: Boolean, val totalMRU: Long, val nodes: List<MIMDependencyEntry>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val MAX_NODES = 4096
        val TYPE = CustomPacketPayload.Type<MIMDependencyResponse>("mim_dependency_response".ecRL)
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MIMDependencyResponse> = StreamCodec.of(
            { buffer, value ->
                buffer.writeVarInt(value.containerId); buffer.writeVarInt(value.requestId); buffer.writeBoolean(value.complete)
                buffer.writeVarLong(value.totalMRU); buffer.writeVarInt(value.nodes.size)
                value.nodes.forEach {
                    buffer.writeVarInt(it.parent); ItemStack.STREAM_CODEC.encode(buffer, it.stack)
                    buffer.writeVarLong(it.amount); buffer.writeVarLong(it.stored); buffer.writeVarInt(it.crafts); buffer.writeVarInt(it.status.ordinal)
                }
            },
            { buffer ->
                val container = buffer.readVarInt()
                val request = buffer.readVarInt()
                val complete = buffer.readBoolean()
                val mru = buffer.readVarLong()
                val size = buffer.readVarInt()
                require(size in 1..MAX_NODES && mru >= 0) { "Invalid MIM dependency response" }
                val nodes = List(size) { index ->
                    val parent = buffer.readVarInt()
                    require(parent in -1 until index) { "Invalid MIM dependency parent" }
                    val stack = ItemStack.STREAM_CODEC.decode(buffer)
                    val amount = buffer.readVarLong()
                    val stored = buffer.readVarLong()
                    val crafts = buffer.readVarInt()
                    val status = MIMDependencyStatus.entries.getOrNull(buffer.readVarInt()) ?: error("Invalid MIM dependency status")
                    require(amount > 0 && stored in 0..amount && crafts >= 0) { "Invalid MIM dependency amounts" }
                    MIMDependencyEntry(parent, stack, amount, stored, crafts, status)
                }
                MIMDependencyResponse(container, request, complete, mru, nodes)
            }
        )

        fun from(containerId: Int, requestId: Int, plan: MIMCraftingPlan): MIMDependencyResponse {
            val nodes = MIMDependencyPresentation.flatten(plan.root, MAX_NODES)
            var total = 0L
            for (step in plan.steps) {
                val cost = MIMCraftingCosts.work(step.pattern).totalMRU
                val amount = if (cost > Long.MAX_VALUE / step.executions) Long.MAX_VALUE else cost * step.executions
                total = if (Long.MAX_VALUE - total < amount) Long.MAX_VALUE else total + amount
            }
            return MIMDependencyResponse(containerId, requestId, plan.complete, total, nodes)
        }
    }
}
