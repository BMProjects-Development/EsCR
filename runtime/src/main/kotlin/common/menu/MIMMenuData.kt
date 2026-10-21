package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.mim.MIMFrames
import com.algorithmlx.ecr.api.mim.MIMFilters
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.common.inventory.*
import io.netty.handler.codec.DecoderException
import net.minecraft.core.BlockPos
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack

data class MIMMenuData(val pos: BlockPos, val kind: MIMMenuKind) {
    companion object {
        val CODEC: StreamCodec<RegistryFriendlyByteBuf, MIMMenuData> = StreamCodec.of(
            { buffer, value -> buffer.writeBlockPos(value.pos); buffer.writeIdentifier(value.kind.id) },
            { buffer -> MIMMenuData(buffer.readBlockPos(), MIMMenuKind.resolve(buffer.readIdentifier())) }
        )

        fun from(device: MIMDeviceEntity): MIMMenuData = MIMMenuData(device.blockPos, device.menuKind)
    }
}

class MIMMenuKind private constructor(
    val id: Identifier,
    val slots: Int,
    val configuration: MIMConfigurationLayout?,
    val terminal: Boolean,
    val accepts: (Int, ItemStack) -> Boolean
) {
    val name: String get() = id.path

    companion object {
        val CORE = register("mim_core".ecRL, 37, MIMConfigurationLayout.CORE) { _, stack -> boundGem(stack) }
        val STORAGE = register("mim_storage".ecRL, 27, MIMConfigurationLayout.STORAGE) { _, stack ->
            boundGem(stack) || stack.item is InventoryGemItem && MIMLinks.isBound(stack)
        }
        val CRAFTING = register("mim_crafting".ecRL, 9, MIMConfigurationLayout.CRAFTING) { _, stack -> MIMFrames.provider(stack) != null }
        val SCREEN = register("mim_screen".ecRL, 0, null, true) { _, _ -> false }
        val NODE = register("mim_node".ecRL, 1, MIMConfigurationLayout.NODE) { _, stack -> MIMFilters.provider(stack) != null }

        @JvmStatic
        fun register(
            id: Identifier, slots: Int, configuration: MIMConfigurationLayout?, terminal: Boolean = false,
            accepts: (Int, ItemStack) -> Boolean
        ): MIMMenuKind {
            require(if (terminal) slots == 0 else slots > 0 && configuration != null) { "Invalid MIM menu layout: $id" }
            val kind = MIMMenuKind(id, slots, configuration, terminal, accepts)
            check(Registry.kinds.putIfAbsent(id, kind) == null) { "Duplicate MIM menu kind: $id" }
            return kind
        }

        fun resolve(id: Identifier): MIMMenuKind = Registry.kinds[id] ?: throw DecoderException("Unknown MIM menu kind: $id")

        private fun boundGem(stack: ItemStack): Boolean =
            stack.item is BoundGem && stack.item !is InventoryGemItem && MIMLinks.isBound(stack)

        private object Registry {
            val kinds = linkedMapOf<Identifier, MIMMenuKind>()
        }
    }
}
