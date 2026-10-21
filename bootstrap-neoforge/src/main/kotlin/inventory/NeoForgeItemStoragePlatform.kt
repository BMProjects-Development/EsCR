package com.algorithmlx.ecr.neoforge.inventory

import com.algorithmlx.ecr.api.inventory.ItemStorageIdentity
import com.algorithmlx.ecr.api.inventory.ItemStoragePlatform
import com.algorithmlx.ecr.api.inventory.ItemStorageTransaction
import com.algorithmlx.ecr.api.inventory.PlatformItemContainer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.neoforge.capabilities.Capabilities
import net.neoforged.neoforge.transfer.ResourceHandler
import net.neoforged.neoforge.transfer.item.ItemResource
import net.neoforged.neoforge.transfer.transaction.Transaction

object NeoForgeItemStoragePlatform : ItemStoragePlatform {
    override fun find(level: Level, pos: BlockPos, side: Direction?): PlatformItemContainer? {
        val serverLevel = level as? ServerLevel ?: return null
        val storage = serverLevel.getCapability(Capabilities.Item.BLOCK, pos, side) ?: return null
        return NeoForgeContainer(storage, level.getBlockEntity(pos) ?: storage)
    }

    @Suppress("DEPRECATION")
    override fun openTransaction(): ItemStorageTransaction = NeoForgeTransaction(Transaction.open(Transaction.getCurrentOpenedTransaction()))

    private class NeoForgeTransaction(val transaction: Transaction) : ItemStorageTransaction {
        override fun commit() = transaction.commit()
        override fun close() = transaction.close()
    }

    private class NeoForgeContainer(private val storage: ResourceHandler<ItemResource>, owner: Any) : PlatformItemContainer() {
        override val storageIdentity = ItemStorageIdentity(owner)
        override fun getContainerSize(): Int = storage.size()
        override fun slotIdentity(slot: Int): Any = ItemStorageIdentity(storage, slot)
        override fun amount(slot: Int): Long = storage.getAmountAsLong(slot)
        override fun capacity(slot: Int, stack: ItemStack): Int = storage.getCapacityAsInt(slot, ItemResource.of(stack))
        override fun totalCapacity(slot: Int, stack: ItemStack): Long = storage.getCapacityAsLong(slot, ItemResource.of(stack))
        override fun getItem(slot: Int): ItemStack = storage.getResource(slot).toStack(storage.getAmountAsInt(slot))

        override fun insert(stack: ItemStack, transaction: ItemStorageTransaction, slot: Int?, mergeExisting: Boolean): Int {
            if (stack.isEmpty) return 0
            val context = (transaction as NeoForgeTransaction).transaction
            val resource = ItemResource.of(stack)
            if (slot == null && mergeExisting) return storage.insert(resource, stack.count, context)
            var inserted = 0
            val targets = if (slot == null) 0 until storage.size() else listOf(slot)
            for (index in targets) {
                if (!mergeExisting && !storage.getResource(index).isEmpty) continue
                inserted += storage.insert(index, resource, stack.count - inserted, context)
                if (inserted == stack.count) break
            }
            return inserted
        }

        override fun extract(slot: Int, stack: ItemStack, amount: Int, transaction: ItemStorageTransaction): Int =
            if (stack.isEmpty || amount <= 0) 0
            else storage.extract(slot, ItemResource.of(stack), amount, (transaction as NeoForgeTransaction).transaction)
    }
}
