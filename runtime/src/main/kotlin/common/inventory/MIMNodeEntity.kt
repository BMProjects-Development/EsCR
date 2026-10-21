package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.inventory.InventoryTransfer
import com.algorithmlx.ecr.api.inventory.InventoryNetwork
import com.algorithmlx.ecr.api.inventory.InventoryAccess
import com.algorithmlx.ecr.common.block.MIMNode
import com.algorithmlx.ecr.api.mim.MIMTransferModule
import com.algorithmlx.ecr.api.mim.MIMFilters
import com.algorithmlx.ecr.api.mim.MIMModules
import com.algorithmlx.ecr.common.menu.MIMMenuKind
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

class MIMNodeEntity(
    pos: BlockPos, state: BlockState, type: BlockEntityType<*>,
    val imports: Boolean, val persistent: Boolean
) : MIMModuleEntity(
    pos, state, type, MIMMenuKind.NODE.slots
), MIMTransferModule {
    override val menuKind get() = MIMMenuKind.NODE
    override val transferOrder get() = if (imports) 0 else 1
    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean = slot in 0 until containerSize && MIMFilters.provider(stack) != null
    override fun getMaxStackSize(): Int = 1

    override fun transfer(network: InventoryNetwork) {
        val level = level ?: return
        if (level.hasNeighborSignal(blockPos)) return
        val facing = blockState.getValue(MIMNode.FACING)
        val connected = InventoryAccess.resolve(level, blockPos.relative(facing), facing.opposite) ?: return
        if (connected.identities.any(MIMModules::isInternal)) return
        val filter = getItem(0)
        val accepts: (ItemStack) -> Boolean = { stack ->
            MIMFilters.matches(filter, stack)
        }
        if (imports) InventoryTransfer.moveToNetwork(connected, network, if (persistent) 1 else 0, accepts)
        else InventoryTransfer.moveFromNetwork(network, connected, persistent, accepts)
    }
}
