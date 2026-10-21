package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.mim.MIMCraftingActivity
import com.algorithmlx.ecr.api.mim.MIMCraftingState
import com.algorithmlx.ecr.api.mim.MIMTerminal
import com.algorithmlx.ecr.api.network.Network
import com.algorithmlx.ecr.api.utils.ecRL
import net.minecraft.core.BlockPos
import net.minecraft.core.UUIDUtil
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import java.util.UUID

data class MIMTaskEntry(
    val module: BlockPos, val id: UUID?, val target: ItemStack, val remaining: Int, val activity: MIMCraftingActivity
)

data class MIMTaskRequest(val containerId: Int, val requestId: Int, val offset: Int) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MIMTaskRequest>("mim_task_request".ecRL)
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MIMTaskRequest> = StreamCodec.of(
            { buffer, value -> buffer.writeVarInt(value.containerId); buffer.writeVarInt(value.requestId); buffer.writeVarInt(value.offset) },
            { buffer -> MIMTaskRequest(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()) }
        )

        fun handle(player: ServerPlayer, request: MIMTaskRequest) {
            val menu = player.containerMenu as? MIMMenu ?: return
            if (menu.containerId != request.containerId || request.offset < 0) return
            val core = (menu.device as? MIMTerminal)?.takeIf { menu.stillValid(player) }?.parent() ?: return
            val tasks = core.craftingModules().flatMap { module ->
                val entries = module.tasks().filter { it.remaining > 0 }.map {
                    MIMTaskEntry(module.blockEntity.blockPos, it.id, it.target, it.remaining, it.activity)
                }
                if (entries.isNotEmpty() || module.pendingCrafts <= 0) entries
                else listOf(MIMTaskEntry(module.blockEntity.blockPos, null, ItemStack.EMPTY, module.pendingCrafts,
                    module.activity ?: MIMCraftingActivity(MIMCraftingState.IDLE, 0, 0, 0)))
            }
            val offset = request.offset.coerceAtMost((tasks.size - MIMTaskResponse.PAGE_SIZE).coerceAtLeast(0))
            Network.sendTo(player, MIMTaskResponse(menu.containerId, request.requestId, offset, tasks.size,
                tasks.drop(offset).take(MIMTaskResponse.PAGE_SIZE)))
        }
    }
}

data class MIMTaskCancel(val containerId: Int, val module: BlockPos, val id: UUID) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE = CustomPacketPayload.Type<MIMTaskCancel>("mim_task_cancel".ecRL)
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MIMTaskCancel> = StreamCodec.of(
            { buffer, value -> buffer.writeVarInt(value.containerId); buffer.writeBlockPos(value.module); UUIDUtil.STREAM_CODEC.encode(buffer, value.id) },
            { buffer -> MIMTaskCancel(buffer.readVarInt(), buffer.readBlockPos(), UUIDUtil.STREAM_CODEC.decode(buffer)) }
        )

        fun handle(player: ServerPlayer, request: MIMTaskCancel) {
            val menu = player.containerMenu as? MIMMenu ?: return
            if (menu.containerId != request.containerId || !menu.stillValid(player)) return
            val core = (menu.device as? MIMTerminal)?.parent() ?: return
            core.craftingModules().filter { it.blockEntity.blockPos == request.module }.firstOrNull { it.cancelTask(request.id) }
            menu.broadcastChanges()
        }
    }
}

data class MIMTaskResponse(
    val containerId: Int, val requestId: Int, val offset: Int, val total: Int, val entries: List<MIMTaskEntry>
) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        const val PAGE_SIZE = 4
        val TYPE = CustomPacketPayload.Type<MIMTaskResponse>("mim_task_response".ecRL)
        val STREAM_CODEC: StreamCodec<RegistryFriendlyByteBuf, MIMTaskResponse> = StreamCodec.of(
            { buffer, value ->
                buffer.writeVarInt(value.containerId); buffer.writeVarInt(value.requestId)
                buffer.writeVarInt(value.offset); buffer.writeVarInt(value.total); buffer.writeVarInt(value.entries.size)
                value.entries.forEach { entry ->
                    buffer.writeBlockPos(entry.module); buffer.writeBoolean(entry.id != null)
                    entry.id?.let { UUIDUtil.STREAM_CODEC.encode(buffer, it) }
                    ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, entry.target)
                    buffer.writeVarInt(entry.remaining); buffer.writeVarInt(entry.activity.state.ordinal)
                    buffer.writeVarInt(entry.activity.completed); buffer.writeVarInt(entry.activity.ticks); buffer.writeVarInt(entry.activity.mruPerTick)
                }
            },
            { buffer ->
                val container = buffer.readVarInt()
                val request = buffer.readVarInt()
                val offset = buffer.readVarInt()
                val total = buffer.readVarInt()
                val size = buffer.readVarInt()
                require(total >= 0 && offset in 0..total && size in 0..PAGE_SIZE && size <= total - offset) { "Invalid MIM task page" }
                val entries = List(size) {
                    val module = buffer.readBlockPos()
                    val id = if (buffer.readBoolean()) UUIDUtil.STREAM_CODEC.decode(buffer) else null
                    val target = ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer)
                    val remaining = buffer.readVarInt()
                    val state = MIMCraftingState.entries.getOrNull(buffer.readVarInt()) ?: error("Invalid MIM task state")
                    val completed = buffer.readVarInt()
                    val ticks = buffer.readVarInt()
                    val mru = buffer.readVarInt()
                    require(remaining > 0 && completed >= 0 && ticks >= 0 && mru >= 0) { "Invalid MIM task progress" }
                    MIMTaskEntry(module, id, target, remaining, MIMCraftingActivity(state, completed, ticks, mru))
                }
                MIMTaskResponse(container, request, offset, total, entries)
            }
        )
    }
}
