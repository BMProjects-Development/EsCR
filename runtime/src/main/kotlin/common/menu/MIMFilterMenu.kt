package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.container.AbstractMenu
import com.algorithmlx.ecr.common.inventory.MIMFilterComponent
import com.algorithmlx.ecr.common.inventory.MIMFilterItem
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.registry.DataComponentRegistry
import com.algorithmlx.ecr.registry.MenuTypeRegistry
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack
import net.minecraft.network.chat.Component

class MIMFilterMenu private constructor(
    containerId: Int,
    private val inventory: Inventory,
    private val filterSlot: Int,
    private val filter: ItemStack
) : AbstractMenu(MenuTypeRegistry.mimFilter, containerId, ContainerLevelAccess.NULL) {
    constructor(containerId: Int, inventory: Inventory) : this(containerId, inventory, -1, ItemStack.EMPTY)
    constructor(containerId: Int, inventory: Inventory, filterSlot: Int) : this(
        containerId, inventory, filterSlot, inventory.getItem(filterSlot)
    )

    private val ghosts = SimpleContainer(MIMFilterComponent.SIZE)
    private val data = SimpleContainerData(6)
    val blacklist: Boolean get() = data.get(BLACKLIST) != 0
    val ignoreComponents: Boolean get() = data.get(COMPONENTS) != 0
    val ignoreDamage: Boolean get() = data.get(DAMAGE) != 0
    val commonTags: Boolean get() = data.get(TAGS) != 0
    val advanced: Boolean get() = data.get(ADVANCED) != 0
    private val lockedSlot: Int get() = data.get(LOCKED_SLOT)

    init {
        data.set(LOCKED_SLOT, filterSlot)
        data.set(ADVANCED, if ((filter.item as? MIMFilterItem)?.advanced == true) 1 else 0)
        val component = filter[DataComponentRegistry.mimFilter.get()] ?: MIMFilterComponent.EMPTY
        data.set(BLACKLIST, if (component.blacklist) 1 else 0)
        data.set(COMPONENTS, if (advanced && component.ignoreComponents) 1 else 0)
        data.set(DAMAGE, if (advanced && component.ignoreDamage) 1 else 0)
        data.set(TAGS, if (advanced && component.commonTags) 1 else 0)
        component.samples.forEachIndexed { index, stack -> ghosts.setItem(index, stack) }
        repeat(MIMFilterComponent.SIZE) { index ->
            addSlot(object : Slot(ghosts, index, GRID_X + index * 18, GRID_Y) {
                override fun mayPlace(stack: ItemStack): Boolean = false
                override fun mayPickup(player: Player): Boolean = false
                override fun remove(amount: Int): ItemStack = ItemStack.EMPTY
                override fun isFake(): Boolean = true
            })
        }
        for (row in 0..2) for (column in 0..8) addSlot(PlayerSlot(9 + row * 9 + column, 8 + column * 18, PLAYER_Y + row * 18))
        repeat(9) { column -> addSlot(PlayerSlot(column, 8 + column * 18, PLAYER_Y + 58)) }
        addDataSlots(data)
    }

    override fun clicked(slotId: Int, button: Int, input: ContainerInput, player: Player) {
        if (!stillValid(player)) return
        if (slotId in 0..<MIMFilterComponent.SIZE) {
            when (input) {
                ContainerInput.PICKUP -> if (button == 0 || button == 1) configure(slotId, if (button == 1) ItemStack.EMPTY else carried)
                ContainerInput.QUICK_MOVE -> configure(slotId, ItemStack.EMPTY)
                else -> Unit
            }
            return
        }
        if (input == ContainerInput.SWAP && button == lockedSlot) return
        super.clicked(slotId, button, input, player)
    }

    override fun quickMoveStack(player: Player, index: Int): ItemStack {
        if (!stillValid(player)) return ItemStack.EMPTY
        val source = slots.getOrNull(index) ?: return ItemStack.EMPTY
        if (index < MIMFilterComponent.SIZE || source.containerSlot == lockedSlot || source.item.isEmpty) return ItemStack.EMPTY
        val empty = (0..<MIMFilterComponent.SIZE).firstOrNull { ghosts.getItem(it).isEmpty } ?: return ItemStack.EMPTY
        configure(empty, source.item)
        return ItemStack.EMPTY
    }

    private fun configure(index: Int, stack: ItemStack) {
        val previous = ghosts.getItem(index)
        ghosts.setItem(index, if (stack.isEmpty) ItemStack.EMPTY else stack.copyWithCount(1))
        if (!component().isValid()) {
            ghosts.setItem(index, previous)
            if (!inventory.player.level().isClientSide) {
                val config = ECConfig.current.inventoryManager
                inventory.player.sendOverlayMessage(Component.translatable(
                    "message.escr.mim.filter_limit", config.filterMaxNodes, config.filterMaxDepth
                ))
            }
            return
        }
        save()
    }

    override fun clickMenuButton(player: Player, id: Int): Boolean {
        if (!stillValid(player)) return false
        when (id) {
            BLACKLIST -> toggle(BLACKLIST)
            COMPONENTS, DAMAGE, TAGS -> if (advanced) toggle(id) else return false
            CLEAR -> ghosts.clearContent()
            else -> return false
        }
        save()
        return true
    }

    private fun toggle(index: Int) { data.set(index, if (data.get(index) == 0) 1 else 0) }

    private fun component(): MIMFilterComponent = MIMFilterComponent(
        (0..<MIMFilterComponent.SIZE).map(ghosts::getItem), blacklist, ignoreComponents, ignoreDamage, commonTags
    )

    private fun save() {
        if (inventory.player.level().isClientSide || !stillValid(inventory.player)) return
        filter.set(DataComponentRegistry.mimFilter.get(), component())
        inventory.setChanged()
        broadcastChanges()
    }

    override fun stillValid(player: Player): Boolean = player === inventory.player &&
        (player.level().isClientSide || filterSlot >= 0 && inventory.getItem(filterSlot) === filter && filter.item is MIMFilterItem)

    override fun canDragTo(slot: Slot): Boolean = slot.container !== ghosts && slot.containerSlot != lockedSlot

    override fun canTakeItemForPickAll(stack: ItemStack, slot: Slot): Boolean =
        slot.container !== ghosts && slot.containerSlot != lockedSlot && super.canTakeItemForPickAll(stack, slot)

    private inner class PlayerSlot(index: Int, x: Int, y: Int) : Slot(inventory, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = containerSlot != lockedSlot
        override fun mayPickup(player: Player): Boolean = containerSlot != lockedSlot
    }

    companion object {
        const val GRID_X = 8
        const val GRID_Y = 34
        const val PLAYER_Y = 154
        const val HEIGHT = 238
        const val BLACKLIST = 0
        const val COMPONENTS = 1
        const val DAMAGE = 2
        const val TAGS = 3
        const val CLEAR = 6
        private const val ADVANCED = 4
        private const val LOCKED_SLOT = 5
    }
}
