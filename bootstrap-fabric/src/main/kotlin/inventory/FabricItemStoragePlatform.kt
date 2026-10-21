package com.algorithmlx.ecr.fabric.inventory

import com.algorithmlx.ecr.api.inventory.ItemStorageIdentity
import com.algorithmlx.ecr.api.inventory.ItemStoragePlatform
import com.algorithmlx.ecr.api.inventory.ItemStorageTransaction
import com.algorithmlx.ecr.api.inventory.PlatformItemContainer
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage
import net.fabricmc.fabric.api.transfer.v1.storage.Storage
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level

object FabricItemStoragePlatform : ItemStoragePlatform {
    override fun find(level: Level, pos: BlockPos, side: Direction?): PlatformItemContainer? {
        val storage = ItemStorage.SIDED.find(level, pos, side) ?: return null
        return FabricContainer(storage, level.getBlockEntity(pos) ?: storage)
    }

    @Suppress("DEPRECATION")
    override fun openTransaction(): ItemStorageTransaction = FabricTransaction(Transaction.openNested(Transaction.getCurrentUnsafe()))

    private class FabricTransaction(val transaction: Transaction) : ItemStorageTransaction {
        override fun commit() = transaction.commit()
        override fun close() = transaction.close()
    }

    private class FabricContainer(private val storage: Storage<ItemVariant>, owner: Any) : PlatformItemContainer() {
        private val views = if (storage is SlottedStorage<ItemVariant>) storage.slots else storage.toList()
        override val storageIdentity = ItemStorageIdentity(owner)
        override fun getContainerSize(): Int = views.size
        override fun slotIdentity(slot: Int): Any = ItemStorageIdentity(views[slot].underlyingView)
        override fun amount(slot: Int): Long = views[slot].amount
        override fun capacity(slot: Int, stack: ItemStack): Int = views[slot].capacity.coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
        override fun totalCapacity(slot: Int, stack: ItemStack): Long = views[slot].capacity
        override fun getItem(slot: Int): ItemStack = views[slot].resource.toStack(amount(slot).coerceIn(0, Int.MAX_VALUE.toLong()).toInt())

        override fun insert(stack: ItemStack, transaction: ItemStorageTransaction, slot: Int?, mergeExisting: Boolean): Int {
            if (stack.isEmpty || !storage.supportsInsertion()) return 0
            val context = (transaction as FabricTransaction).transaction
            val resource = ItemVariant.of(stack)
            if (slot == null && mergeExisting) return storage.insert(resource, stack.count.toLong(), context).toInt()
            var inserted = 0
            val targets = if (slot == null) views.indices else listOf(slot)
            for (index in targets) {
                val view = views[index]
                if (!mergeExisting && !view.isResourceBlank) continue
                val target = insertableView(view) ?: continue
                inserted += target.insert(resource, (stack.count - inserted).toLong(), context).toInt()
                if (inserted == stack.count) break
            }
            return inserted
        }

        override fun extract(slot: Int, stack: ItemStack, amount: Int, transaction: ItemStorageTransaction): Int =
            if (stack.isEmpty || amount <= 0 || !storage.supportsExtraction())
                0
            else
                views[slot].extract(ItemVariant.of(stack), amount.toLong(), (transaction as FabricTransaction).transaction).toInt()

        @Suppress("UNCHECKED_CAST")
        private fun insertableView(view: StorageView<ItemVariant>): Storage<ItemVariant>? = view as? Storage<ItemVariant>
    }
}
