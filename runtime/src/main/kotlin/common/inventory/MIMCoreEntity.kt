package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.api.inventory.InventoryNetwork
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.mim.*
import com.algorithmlx.ecr.common.menu.MIMMenuKind
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.util.ProblemReporter
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.TagValueOutput
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class MIMCoreEntity(pos: BlockPos, state: BlockState) : MIMDeviceEntity(
    pos, state, BlockEntityTypeRegistry.mimCore.get(), MIMMenuKind.CORE.slots,
    ECConfig.current.inventoryManager.coreCapacity, 0
), MIMManager, MIMExpandableContainer {
    override val blockEntity get() = this
    override val power get() = mruStorage
    override val menuKind get() = MIMMenuKind.CORE
    private var mutableAvailableSlots = MIMMenuKind.CORE.slots
    private var migratePowerLink = false
    override val availableSlots: Int get() = mutableAvailableSlots

    private fun updateCapacity() {
        mutableAvailableSlots = MIMMenuKind.CORE.configuration!!.availableSlots(containerSize) { !getItem(it).isEmpty }
        growInventory(availableSlots)
    }

    override fun setChanged() {
        if (level?.isClientSide != true) updateCapacity()
        super.setChanged()
    }

    override fun saveInventory(output: ValueOutput) {
        MIMCoreInventoryData.write(output, getItems())
        output.putBoolean(LEGACY_POWER_LINK_TAG, migratePowerLink)
    }

    override fun loadInventory(input: ValueInput) {
        migratePowerLink = MIMCoreInventoryData.isLegacy(input) || input.getBooleanOr(LEGACY_POWER_LINK_TAG, false)
        setItems(MIMCoreInventoryData.read(input))
        updateCapacity()
    }

    override fun clearContent() {
        super.clearContent()
        setChanged()
    }

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag {
        val output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries)
        saveDeviceProperties(output)
        return output.buildResult()
    }

    override fun insertConfiguration(stack: ItemStack): ItemStack {
        val remainder = stack.copy()
        var index = 0
        while (!remainder.isEmpty && index < availableSlots) {
            if (getItem(index).isEmpty && canPlaceItem(index, remainder)) {
                setItem(index, remainder.copyWithCount(1))
                remainder.shrink(1)
            }
            index++
        }
        return remainder
    }

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean {
        if (slot !in 0 until containerSize || stack.item !is BoundGem || stack.item is InventoryGemItem || !MIMLinks.isBound(stack)) return false
        val level = level ?: return true
        if (slot == 0) {
            val source = MIMLinks.resolveMRU(level, stack) ?: return false
            return source !== this && source.deviceType.isExporter && source.mruStorage.isSameTypes(mruStorage)
        }
        val target = MIMLinks.resolve(level, stack)
        if (target?.let(MIMModules::inventory) != null) return false
        return target?.let(MIMModules::resolve) != null
    }

    override fun getMaxStackSize(): Int = 1

    private fun devices(): List<BlockEntity> {
        val level = level ?: return emptyList()
        return (1 until containerSize).mapNotNull { slot ->
            val stack = getItem(slot)
            if (stack.item is InventoryGemItem) null else MIMLinks.resolve(level, stack)
        }.filterNot { it === this }.distinct()
    }

    override fun isLinked(module: MIMModule): Boolean = devices().any { it === module.blockEntity }

    fun modules(): List<MIMModule> = devices().flatMap(MIMModules::all).filter { it.attach(this) }

    override fun network(): InventoryNetwork = InventoryNetwork(modules().filterIsInstance<MIMStorageProvider>().flatMap { it.endpoints() })

    override fun craftingModules(): List<MIMCraftingProvider> = modules().filterIsInstance<MIMCraftingProvider>()

    override fun serverTick() {
        migratePowerLink()
        super.serverTick()
        val level = level ?: return
        val modules = modules()
        val context = MIMContext(this, level.gameTime % ECConfig.current.inventoryManager.operationTicks == 0L)
        modules.forEach { it.managerTick(context) }
        if (!context.operationDue) return
        modules.filterIsInstance<MIMTransferModule>().sortedBy { it.transferOrder }.forEach { it.transfer(context.network) }
    }

    private fun migratePowerLink() {
        if (!migratePowerLink) return
        if (getItem(0).isEmpty) {
            val source = (1 until containerSize).firstOrNull { canPlaceItem(0, getItem(it)) } ?: return
            val stack = getItem(source)
            setItem(source, ItemStack.EMPTY)
            setItem(0, stack)
        }
        migratePowerLink = false
        setChanged()
    }

    companion object {
        private const val LEGACY_POWER_LINK_TAG = "legacy_power_link"
    }
}
