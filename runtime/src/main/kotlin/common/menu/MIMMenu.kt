package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.container.AbstractMenu
import com.algorithmlx.ecr.api.item.BoundGemSlotContext
import com.algorithmlx.ecr.api.mim.MIMExpandableContainer
import com.algorithmlx.ecr.api.mim.MIMTerminal
import com.algorithmlx.ecr.api.mim.MIMCraftingPlanner
import com.algorithmlx.ecr.api.mim.MIMCraftingPlan
import com.algorithmlx.ecr.api.mim.MIMCraftingState
import com.algorithmlx.ecr.api.mim.MIMDependencyNode
import com.algorithmlx.ecr.api.mim.MIMDependencyStatus
import com.algorithmlx.ecr.api.container.slot.VanillaSpecialSlot
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.common.inventory.MIMDeviceEntity
import com.algorithmlx.ecr.mixin.SlotPositionAccessor
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.registry.MenuTypeRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.server.level.ServerPlayer
import net.minecraft.util.Prediction
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import java.util.Locale
import net.minecraft.core.BlockPos

class MIMMenu private constructor(
    containerId: Int,
    private val inventory: Inventory,
    private val container: Container,
    val device: MIMDeviceEntity?,
    val kind: MIMMenuKind,
    private val devicePos: BlockPos
) : AbstractMenu(MenuTypeRegistry.mim, containerId, ContainerLevelAccess.NULL), BoundGemSlotContext {
    constructor(containerId: Int, inventory: Inventory, device: MIMDeviceEntity) : this(
        containerId, inventory,
        if (device is MIMExpandableContainer) MIMInventoryWindow(
            device, device.menuKind.slots, device.menuKind.configuration!!.columns, device.menuKind.configuration!!.fixedSlots.size
        ) else device,
        device, MIMMenuData.from(device).kind, device.blockPos
    )

    constructor(containerId: Int, inventory: Inventory, data: MIMMenuData) : this(
        containerId, inventory,
        SimpleContainer(data.kind.slots),
        inventory.player.level().getBlockEntity(data.pos) as? MIMDeviceEntity,
        data.kind, data.pos
    )

    val isTerminal = kind.terminal
    val isCore = kind.configuration?.expanding == true
    val configurationLayout = kind.configuration
    val playerY: Int get() = if (isTerminal) MIMTerminalLayout.playerY(visibleRows) else configurationLayout!!.playerY
    val screenWidth: Int get() = if (isTerminal) MIMTerminalLayout.WIDTH else configurationLayout!!.width
    val screenHeight: Int get() = if (isTerminal) MIMTerminalLayout.height(visibleRows) else configurationLayout!!.height
    val deviceSlots: Int get() = container.containerSize
    private val layout = MIMSlotLayout(if (isTerminal) PAGE_SIZE else 0, deviceSlots)
    val playerStart: Int get() = layout.playerStart
    private val display = SimpleContainer(PAGE_SIZE)
    private var entries = emptyList<Entry>()
    private var query = ""
    private var requestedRows = MIMTerminalLayout.MIN_ROWS
    private var nextRefreshTick = Long.MIN_VALUE
    private val values = IntArray(DATA_SIZE).apply {
        this[VISIBLE_ROWS] = MIMTerminalLayout.MIN_ROWS
        this[AVAILABLE_SLOTS] = configurationLayout?.let { minOf(deviceSlots, it.rows * it.columns) } ?: deviceSlots
    }
    private val data = object : ContainerData {
        override fun get(index: Int): Int = values[index]
        override fun set(index: Int, value: Int) { values[index] = value }
        override fun getCount(): Int = values.size
    }

    init {
        require(deviceSlots == kind.slots) { "MIM device inventory does not match menu ${kind.id}" }
        container.startOpen(inventory.player)
        if (isTerminal)
            repeat(PAGE_SIZE) { index ->
                addSlot(object : Slot(display, index, MIMTerminalLayout.GRID_X + index % 9 * 18, MIMTerminalLayout.GRID_Y + index / 9 * 18) {
                    override fun mayPlace(stack: ItemStack): Boolean = false
                    override fun mayPickup(player: Player): Boolean = false
                    override fun isFake(): Boolean = true
                    override fun isActive(): Boolean = isStorageAvailable && index < visibleRows * MIMTerminalLayout.COLUMNS
                })
            }
        repeat(deviceSlots) { index ->
            val x = configurationLayout!!.slotX(index)
            val y = configurationLayout.slotY(index, 0)
            addSlot(VanillaSpecialSlot(container, index, x, y, { stack ->
                canPlaceConfiguration(index, stack)
            }, stackSize = 1))
        }
        inventory.make(if (isTerminal) MIMTerminalLayout.PLAYER_X else 8, playerY)
        addDataSlots(data)
        refresh()
    }

    private fun canPlaceConfiguration(index: Int, stack: ItemStack): Boolean {
        if (!inventory.player.level().isClientSide) return container.canPlaceItem(index, stack)
        return kind.accepts(index, stack)
    }

    val scrollRow: Int get() = readInt(0)
    val availableSlots: Int get() = if (isCore) readInt(AVAILABLE_SLOTS).coerceAtLeast(deviceSlots) else deviceSlots
    val totalRows: Int get() = configurationLayout?.totalRows(availableSlots) ?: readInt(2).coerceAtLeast(1)
    val visibleRows: Int get() = configurationLayout?.rows ?: values[VISIBLE_ROWS].coerceIn(1, MIMTerminalLayout.MAX_ROWS)
    val maxScrollRow: Int get() = (totalRows - visibleRows).coerceAtLeast(0)
    val hasPower: Boolean get() = values[POWERED] != 0
    val mru: Int get() = readInt(4)
    val mruCapacity: Int get() = readInt(6)
    val isConnected: Boolean get() = values[8] != 0
    val isStorageAvailable: Boolean get() = isConnected && hasPower
    val hasRecipes: Boolean get() = values[HAS_RECIPES] != 0
    val isCraftingMode: Boolean get() = values[MODE] != 0
    val queuedCrafts: Int get() = readInt(9)
    val craftCompleted: Int get() = readInt(ACTIVE_COMPLETED)
    val craftTicks: Int get() = readInt(ACTIVE_TICKS)
    val craftMRUPerTick: Int get() = readInt(ACTIVE_MRU)
    val craftingState: MIMCraftingState? get() = MIMCraftingState.entries.getOrNull(values[ACTIVE_STATE] - 1)
    val coreMRU: Int get() = readInt(CORE_MRU)
    val coreMRUCapacity: Int get() = readInt(CORE_CAPACITY)

    fun count(index: Int): Long {
        if (index !in 0..<PAGE_SIZE) return 0
        return (0..3).fold(0L) { result, part ->
            result or ((values[HEADER_SIZE + index * 5 + part].toLong() and 0xFFFF) shl (part * 16))
        }
    }

    fun canCraft(index: Int): Boolean = index in 0..<PAGE_SIZE && values[HEADER_SIZE + index * 5 + 4] != 0

    override fun hasBoundGemTooltip(slot: Slot): Boolean = slot.index in layout.displaySlots until playerStart

    fun setSearch(player: Player, search: String) {
        if (!isTerminal || !stillValid(player)) return
        query = search.take(MAX_SEARCH).trim().lowercase(Locale.ROOT)
        writeInt(0, 0)
        invalidate()
        broadcastChanges()
    }

    fun setViewport(player: Player, maxRows: Int, row: Int) {
        if (!stillValid(player) || player.level().isClientSide) return
        if (!isTerminal) {
            refresh()
            if (scrollRow != row.coerceIn(0, maxScrollRow)) resetQuickCraft()
            writeInt(0, row.coerceIn(0, maxScrollRow))
            (container as? MIMInventoryWindow)?.select(scrollRow)
            updateSlotPositions()
            super.broadcastChanges()
            return
        }
        requestedRows = maxRows.coerceIn(1, MIMTerminalLayout.MAX_ROWS)
        writeInt(0, row.coerceAtLeast(0))
        invalidate()
        broadcastChanges()
    }

    fun updateSlotPositions() {
        repeat(deviceSlots) { index ->
            val slotIndex = layout.displaySlots + index
            val y = configurationLayout!!.slotY(index, 0)
            (slots[slotIndex] as SlotPositionAccessor).setY(y)
        }
        for (index in playerStart until slots.size) {
            val playerIndex = index - playerStart
            val y = playerY + if (playerIndex < 27) playerIndex / 9 * 18 else 58
            (slots[index] as SlotPositionAccessor).setY(y)
        }
    }

    override fun broadcastChanges() {
        refresh()
        super.broadcastChanges()
    }

    private fun refresh() {
        if (inventory.player.level().isClientSide) return
        if (!isTerminal) {
            writeInt(4, device?.mruStorage?.mru ?: 0)
            writeInt(6, device?.mruStorage?.mruCapacity ?: 0)
            writeInt(AVAILABLE_SLOTS, (device as? MIMExpandableContainer)?.availableSlots ?: deviceSlots)
            val previousRow = scrollRow
            writeInt(0, scrollRow.coerceIn(0, maxScrollRow))
            if (previousRow != scrollRow) resetQuickCraft()
            (container as? MIMInventoryWindow)?.select(scrollRow)
            updateSlotPositions()
            return
        }
        val screen = device as MIMTerminal
        val tick = inventory.player.level().gameTime
        val core = screen.parent()
        if (tick < nextRefreshTick && hasPower == screen.canWithdraw && isConnected == (core != null)) return
        nextRefreshTick = tick + 5
        values[8] = if (core != null) 1 else 0
        values[POWERED] = if (screen.canWithdraw) 1 else 0
        writeInt(4, core?.power?.mru ?: 0)
        writeInt(6, core?.power?.mruCapacity ?: 0)
        val craftingModules = core?.craftingModules().orEmpty()
        val activity = craftingModules.mapNotNull { it.activity }.let { activities ->
            activities.firstOrNull { it.state == MIMCraftingState.WORKING } ?: activities.firstOrNull()
        }
        writeInt(ACTIVE_COMPLETED, activity?.completed ?: 0)
        writeInt(ACTIVE_TICKS, activity?.ticks ?: 0)
        writeInt(ACTIVE_MRU, activity?.mruPerTick ?: 0)
        values[ACTIVE_STATE] = activity?.state?.ordinal?.plus(1) ?: 0
        writeInt(CORE_MRU, core?.power?.mru ?: 0)
        writeInt(CORE_CAPACITY, core?.power?.mruCapacity ?: 0)
        writeInt(9, craftingModules.sumOf { it.pendingCrafts.toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        val patterns = craftingModules.flatMap { it.patterns() }.filterNot(ItemStack::isEmpty)
        values[HAS_RECIPES] = if (patterns.isEmpty()) 0 else 1
        if (!hasRecipes) values[MODE] = 0
        val available = mutableListOf<Entry>()
        if (isStorageAvailable && isCraftingMode) {
            patterns.forEach { pattern ->
                if (available.none { ItemStack.isSameItemSameComponents(it.stack, pattern) })
                    available += Entry(pattern.copyWithCount(1), 0, true)
            }
        } else if (isStorageAvailable) {
            core?.network()?.snapshot()?.forEach { available += Entry(it.stack.copyWithCount(1), it.count, false) }
        }
        val filtered = available.filter { entry ->
            query.isEmpty() || entry.stack.hoverName.string.lowercase(Locale.ROOT).contains(query) ||
                BuiltInRegistries.ITEM.getKey(entry.stack.item).toString().contains(query)
        }.sortedWith(compareBy({ it.stack.hoverName.string.lowercase(Locale.ROOT) }, { BuiltInRegistries.ITEM.getKey(it.stack.item).toString() }))
        val window = MIMTerminalLayout.window(filtered.size, requestedRows, scrollRow)
        writeInt(2, window.totalRows)
        writeInt(0, window.scrollRow)
        values[VISIBLE_ROWS] = if (isStorageAvailable) window.visibleRows else visibleRows.coerceAtMost(requestedRows)
        entries = filtered.drop(window.firstItem).take(window.visibleSlots)
        repeat(PAGE_SIZE) { index ->
            val entry = entries.getOrNull(index)
            display.setItem(index, entry?.stack?.copy() ?: ItemStack.EMPTY)
            repeat(4) { part -> values[HEADER_SIZE + index * 5 + part] = (((entry?.count ?: 0) ushr (part * 16)) and 0xFFFF).toInt() }
            values[HEADER_SIZE + index * 5 + 4] = if (entry?.craftable == true) 1 else 0
        }
        updateSlotPositions()
    }

    override fun clicked(slotId: Int, button: Int, input: ContainerInput, player: Player) {
        if (slotId in slots.indices && !slots[slotId].isActive) return
        if (isTerminal && slotId in 0..<PAGE_SIZE) {
            if (!isStorageAvailable) return
            if (slotId >= visibleRows * MIMTerminalLayout.COLUMNS) return
            if (!player.level().isClientSide && stillValid(player)) {
                when (input) {
                    ContainerInput.PICKUP -> if (button == 0 || button == 1)
                        if (!carried.isEmpty) carried = (device as MIMTerminal).deposit(carried)
                        else if (!isCraftingMode) withdraw(slotId, if (button == 1) 1 else Int.MAX_VALUE, false)
                    ContainerInput.QUICK_MOVE -> if (!isCraftingMode) withdraw(slotId, Int.MAX_VALUE, true)
                    else -> Unit
                }
                invalidate()
                broadcastChanges()
            }
            return
        }
        super.clicked(slotId, button, input, player)
    }

    fun clickCore(player: Player, row: Int, slot: Int, button: Int, input: ContainerInput) {
        if (!isCore || player.level().isClientSide || !stillValid(player)) return
        refresh()
        if (row != scrollRow) {
            resetQuickCraft()
            broadcastFullState()
            return
        }
        if (slot !in slots.indices && slot != SLOT_CLICKED_OUTSIDE) return
        clicked(slot, button, input, player)
        broadcastChanges()
    }

    private fun withdraw(index: Int, amount: Int, intoInventory: Boolean) {
        val entry = entries.getOrNull(index) ?: return
        val limit = minOf(amount, entry.stack.maxStackSize)
        val capacity = if (intoInventory) inventoryCapacity(entry.stack).coerceAtMost(limit) else limit
        if (capacity <= 0 || (!intoInventory && !carried.isEmpty)) return
        val result = (device as? MIMTerminal)?.withdraw(entry.stack, capacity) ?: return
        if (result.isEmpty) return
        if (intoInventory) {
            inventory.add(result)
            if (!result.isEmpty)
                if (carried.isEmpty) carried = result else inventory.player.drop(result, false, Prediction.SERVER_ONLY)
        }
        else
            carried = result
    }

    private fun inventoryCapacity(template: ItemStack): Int {
        var capacity = 0
        repeat(36) { index ->
            val stack = inventory.getItem(index)
            if (stack.isEmpty)
                capacity += inventory.getMaxStackSize(template)
            else if (ItemStack.isSameItemSameComponents(stack, template)) capacity += (inventory.getMaxStackSize(stack) - stack.count).coerceAtLeast(0)
        }
        return capacity
    }

    override fun clickMenuButton(player: Player, id: Int): Boolean {
        if (!isTerminal || !stillValid(player) || player.level().isClientSide) return false
        when (id) {
            PREVIOUS -> writeInt(0, (scrollRow - visibleRows).coerceAtLeast(0))
            NEXT -> writeInt(0, (scrollRow + visibleRows).coerceAtMost(maxScrollRow))
            CANCEL_CRAFTING -> (device as MIMTerminal).parent()?.craftingModules()?.forEach { it.cancelCrafting() }
            TOGGLE_MODE -> {
                refresh()
                if (!hasRecipes || !isStorageAvailable) return false
                values[MODE] = if (isCraftingMode) 0 else 1
                writeInt(0, 0)
            }
            else -> return false
        }
        invalidate()
        broadcastChanges()
        return true
    }

    fun requestCraft(player: Player, expectedItemStack: ItemStack, batches: Int) {
        if (!isTerminal || player.level().isClientSide || !stillValid(player) || batches <= 0 || expectedItemStack.isEmpty) return
        val screen = device as? MIMTerminal ?: return
        if (!hasPower || !screen.canWithdraw || batches > ECConfig.current.inventoryManager.maxQueuedCrafts) return
        if (dependencyPlan(player, expectedItemStack, batches)?.complete != true) return
        craft(expectedItemStack, batches)
        invalidate()
        broadcastChanges()
    }

    fun dependencyPlan(player: Player, expected: ItemStack, batches: Int): MIMCraftingPlan? {
        if (!isTerminal || player.level().isClientSide || !stillValid(player) || expected.isEmpty || batches <= 0) return null
        val config = ECConfig.current.inventoryManager
        if (batches > config.maxQueuedCrafts)
            return MIMCraftingPlan(MIMDependencyNode(expected.copyWithCount(1), batches.toLong(), 0, 0, MIMDependencyStatus.LIMIT), emptyList(), false)
        val core = (device as? MIMTerminal)?.parent() ?: return null
        val patterns = core.craftingModules().flatMap { it.craftingPatterns() }
        return MIMCraftingPlanner(patterns, core.network().snapshot(), minOf(config.dependencyMaxNodes, MIMDependencyResponse.MAX_NODES - 1),
            config.dependencyMaxDepth, config.dependencyMaxCrafts).plan(expected, batches)
    }

    private fun craft(target: ItemStack, batches: Int) {
        val screen = device as? MIMTerminal ?: return
        if (!hasPower || !screen.canWithdraw) return
        val core = screen.parent() ?: return
        var remaining = batches
        for (module in core.craftingModules()) {
            remaining -= module.craft(core, target, remaining)
            if (remaining <= 0) break
        }
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        if (!stillValid(player)) return ItemStack.EMPTY
        if (isTerminal && index in 0..<PAGE_SIZE) {
            if (player.level().isClientSide || !isStorageAvailable || isCraftingMode || !slots[index].isActive) return ItemStack.EMPTY
            withdraw(index, Int.MAX_VALUE, true)
            invalidate()
            return ItemStack.EMPTY
        }
        val slot = slots.getOrNull(index) ?: return ItemStack.EMPTY
        if (!slot.isActive || !slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY
        val stack = slot.item
        val original = stack.copy()
        if (isCore && index >= playerStart) {
            if (player.level().isClientSide) return ItemStack.EMPTY
            val remainder = (device as MIMExpandableContainer).insertConfiguration(stack)
            if (remainder.count < original.count) {
                slot.set(remainder)
                slot.onTake(player, original.copyWithCount(original.count - remainder.count))
                return original
            }
            if (!layout.transferWithinPlayerInventory(stack, index, ::moveItemStackTo)) return ItemStack.EMPTY
        }
        else if (index >= playerStart && isTerminal) {
            if (player.level().isClientSide) return ItemStack.EMPTY
            val remainder = (device as MIMTerminal).deposit(stack)
            if (remainder.count < stack.count) {
                slot.set(remainder)
                slot.onTake(player, original.copyWithCount(original.count - remainder.count))
                invalidate()
                return original
            }
            if (!layout.transferWithinPlayerInventory(stack, index, ::moveItemStackTo)) return ItemStack.EMPTY
        }
        else if (!layout.transfer(stack, index, ::moveItemStackTo)) return ItemStack.EMPTY
        if (stack.isEmpty) slot.set(ItemStack.EMPTY) else slot.setChanged()
        slot.onTake(player, original.copyWithCount(original.count - stack.count))
        return original
    }

    override fun canTakeItemForPickAll(stack: ItemStack, slot: Slot): Boolean =
        slot.isActive && slot.container !== display && super.canTakeItemForPickAll(stack, slot)

    override fun canDragTo(slot: Slot): Boolean = slot.isActive && slot.container !== display && super.canDragTo(slot)

    override fun stillValid(player: Player): Boolean = player === inventory.player &&
        (player.level().isClientSide || device?.let {
            !it.isRemoved && player.level().getBlockEntity(devicePos) === it &&
                player.distanceToSqr(devicePos.x + 0.5, devicePos.y + 0.5, devicePos.z + 0.5) <= 64.0
        } == true)

    override fun removed(player: Player) {
        super.removed(player)
        container.stopOpen(player)
    }

    private fun writeInt(index: Int, value: Int) {
        values[index] = value and 0xFFFF
        values[index + 1] = value ushr 16
    }

    private fun readInt(index: Int): Int = (values[index] and 0xFFFF) or ((values[index + 1] and 0xFFFF) shl 16)
    private fun invalidate() { nextRefreshTick = Long.MIN_VALUE }
    private data class Entry(val stack: ItemStack, val count: Long, val craftable: Boolean)

    companion object {
        const val PAGE_SIZE = MIMTerminalLayout.MAX_SLOTS
        const val MAX_SEARCH = 96
        const val PREVIOUS = 0
        const val NEXT = 1
        const val CANCEL_CRAFTING = 3
        const val TOGGLE_MODE = 4
        private const val VISIBLE_ROWS = 11
        private const val POWERED = 12
        private const val AVAILABLE_SLOTS = 13
        private const val ACTIVE_COMPLETED = 15
        private const val ACTIVE_TICKS = 17
        private const val ACTIVE_MRU = 19
        private const val ACTIVE_STATE = 21
        private const val CORE_MRU = 22
        private const val CORE_CAPACITY = 24
        private const val MODE = 26
        private const val HAS_RECIPES = 27
        private const val HEADER_SIZE = 28
        private const val DATA_SIZE = HEADER_SIZE + PAGE_SIZE * 5
    }
}

data class MIMSearchPayload(val containerId: Int, val search: String) : CustomPacketPayload {
    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        @JvmField val TYPE = CustomPacketPayload.Type<MIMSearchPayload>("mim_search".ecRL)
        @JvmField val STREAM_CODEC: StreamCodec<FriendlyByteBuf, MIMSearchPayload> = StreamCodec.of(
            { buffer, value -> buffer.writeVarInt(value.containerId); buffer.writeUtf(value.search, MIMMenu.MAX_SEARCH) },
            { buffer -> MIMSearchPayload(buffer.readVarInt(), buffer.readUtf(MIMMenu.MAX_SEARCH)) }
        )

        fun handle(player: ServerPlayer, payload: MIMSearchPayload) {
            val menu = player.containerMenu as? MIMMenu ?: return
            if (menu.containerId == payload.containerId) menu.setSearch(player, payload.search)
        }
    }
}
