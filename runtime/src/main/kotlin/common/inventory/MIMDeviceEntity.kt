package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.block.entity.SynchronizedContainerBlockEntity
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.loadMRUData
import com.algorithmlx.ecr.api.mru.processReceive
import com.algorithmlx.ecr.api.mru.saveMRUData
import com.algorithmlx.ecr.api.mru.storage.MRUStorageContainer
import com.algorithmlx.ecr.common.menu.MIMMenu
import com.algorithmlx.ecr.common.menu.MIMMenuKind
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Prediction
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.world.ContainerHelper
import net.minecraft.world.Containers
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import kotlin.jvm.optionals.getOrNull

abstract class MIMDeviceEntity(
    pos: BlockPos,
    state: BlockState,
    type: BlockEntityType<*>,
    size: Int,
    capacity: Int = 0,
    protected val energySlot: Int = -1
) : SynchronizedContainerBlockEntity(type, pos, state), MRUDevice {
    abstract val menuKind: MIMMenuKind
    private var inventory = NonNullList.withSize(size, ItemStack.EMPTY)
    private val pendingItems = mutableListOf<ItemStack>()
    protected open val previousInventorySize: Int get() = containerSize
    override val mruStorage = MRUStorageContainer(capacity, MRUTypeRegistry.radiationUnit) { setChanged() }
    override val balance = MRUBalanceContainer { setChanged() }
    override val deviceType = MRUDevice.DeviceType.CONNECTABLE_RECEIVER
    override val locator: MRUDevice.LocatorData? = if (energySlot >= 0) MRUDevice.LocatorData(this, energySlot) else null

    override fun getContainerSize(): Int = inventory.size
    override fun getItems(): NonNullList<ItemStack> = inventory
    override fun setItems(items: NonNullList<ItemStack>) { inventory = items }
    override fun getDefaultName(): Component = blockState.block.name
    override fun createMenu(containerId: Int, inventory: Inventory): AbstractContainerMenu {
        if (inventory.player is ServerPlayer && pendingItems.isNotEmpty()) {
            pendingItems.forEach { inventory.placeItemBackInInventory(it, Prediction.SERVER_ONLY) }
            pendingItems.clear()
            setChanged()
        }
        return MIMMenu(containerId, inventory, this)
    }
    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean =
        slot == energySlot && stack.item is BoundGem && stack.item !is InventoryGemItem && MIMLinks.isBound(stack)

    open fun serverTick() { level?.let { processReceive(it) } }

    override fun saveAdditional(output: ValueOutput) {
        saveDeviceProperties(output)
        saveInventory(output)
        if (pendingItems.isNotEmpty()) output.store(PENDING_ITEMS_TAG, ItemStack.CODEC.listOf(), pendingItems)
    }

    protected fun saveDeviceProperties(output: ValueOutput) {
        super.saveAdditional(output)
        saveMRUData(output)
    }

    protected open fun saveInventory(output: ValueOutput) { ContainerHelper.saveAllItems(output, inventory) }

    protected fun growInventory(size: Int) {
        if (size <= inventory.size) return
        val expanded = NonNullList.withSize(size, ItemStack.EMPTY)
        inventory.forEachIndexed { index, stack -> expanded[index] = stack }
        inventory = expanded
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        pendingItems.clear()
        pendingItems.addAll(input.read(PENDING_ITEMS_TAG, ItemStack.CODEC.listOf()).getOrNull().orEmpty().filterNot(ItemStack::isEmpty))
        loadInventory(input)
        loadMRUData(input)
    }

    protected open fun loadInventory(input: ValueInput) {
        val saved = NonNullList.withSize(maxOf(containerSize, previousInventorySize), ItemStack.EMPTY)
        ContainerHelper.loadAllItems(input, saved)
        inventory.indices.forEach { inventory[it] = saved[it] }
        saved.drop(containerSize).filterNotTo(pendingItems, ItemStack::isEmpty)
    }

    override fun preRemoveSideEffects(pos: BlockPos, state: BlockState) {
        super.preRemoveSideEffects(pos, state)
        val level = level ?: return
        if (level.isClientSide) return
        pendingItems.forEach { Containers.dropItemStack(level, pos.x + 0.5, pos.y + 0.5, pos.z + 0.5, it) }
        pendingItems.clear()
    }

    companion object {
        private const val PENDING_ITEMS_TAG = "legacy_pending_inputs"
    }
}
