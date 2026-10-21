package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.container.AbstractMenu
import com.algorithmlx.ecr.common.inventory.CraftingFrameComponent
import com.algorithmlx.ecr.registry.DataComponentRegistry
import com.algorithmlx.ecr.registry.ItemRegistry
import com.algorithmlx.ecr.registry.MenuTypeRegistry
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerInput
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.inventory.Slot
import net.minecraft.world.item.ItemStack

class CraftingFrameMenu private constructor(
    containerId: Int,
    private val inventory: Inventory,
    private val frameSlot: Int,
    private val frame: ItemStack
) : AbstractMenu(MenuTypeRegistry.craftingFrame, containerId, ContainerLevelAccess.NULL) {
    constructor(containerId: Int, inventory: Inventory) : this(containerId, inventory, -1, ItemStack.EMPTY)
    constructor(containerId: Int, inventory: Inventory, frameSlot: Int) : this(
        containerId, inventory, frameSlot, inventory.getItem(frameSlot)
    )

    private val ghosts = SimpleContainer(10)
    private val data = SimpleContainerData(2)
    val substituteIngredients: Boolean get() = data.get(DATA_SUBSTITUTE) != 0
    val hasRecipe: Boolean get() = !ghosts.getItem(OUTPUT_SLOT).isEmpty
    private val lockedSlot: Int get() = data.get(DATA_FRAME_SLOT)

    init {
        data.set(DATA_FRAME_SLOT, frameSlot)
        val encoded = frame[DataComponentRegistry.craftingFrame.get()] ?: CraftingFrameComponent.EMPTY
        data.set(DATA_SUBSTITUTE, if (encoded.substituteIngredients) 1 else 0)
        encoded.ingredients.forEachIndexed { index, stack -> ghosts.setItem(index, stack) }
        for (index in 0 until CraftingFrameComponent.GRID_SIZE) {
            addSlot(GhostSlot(index, GRID_X + index % 3 * 18, GRID_Y + index / 3 * 18))
        }
        addSlot(GhostSlot(OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y))
        for (row in 0..2) for (column in 0..8) {
            addSlot(PlayerSlot(column + row * 9 + 9, 8 + column * 18, PLAYER_Y + row * 18))
        }
        for (column in 0..8) addSlot(PlayerSlot(column, 8 + column * 18, PLAYER_Y + 58))
        addDataSlots(data)
        refreshOutput()
    }

    override fun clicked(slotId: Int, button: Int, input: ContainerInput, player: Player) {
        if (!stillValid(player)) return
        if (slotId in 0 until CraftingFrameComponent.GRID_SIZE) {
            when (input) {
                ContainerInput.PICKUP -> configure(slotId, if (button == 1) ItemStack.EMPTY else carried)
                ContainerInput.QUICK_MOVE -> configure(slotId, ItemStack.EMPTY)
                else -> Unit
            }
            return
        }
        if (slotId == OUTPUT_SLOT) return
        if (input == ContainerInput.SWAP && button == lockedSlot) return
        super.clicked(slotId, button, input, player)
    }

    override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack {
        if (!stillValid(player)) return ItemStack.EMPTY
        val source = slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
        if (slotIndex < PLAYER_START || source.containerSlot == lockedSlot) return ItemStack.EMPTY
        val firstEmpty = (0 until CraftingFrameComponent.GRID_SIZE).firstOrNull { ghosts.getItem(it).isEmpty }
            ?: return ItemStack.EMPTY
        configure(firstEmpty, source.item)
        return ItemStack.EMPTY
    }

    override fun clickMenuButton(player: Player, id: Int): Boolean {
        if (!stillValid(player)) return false
        when (id) {
            TOGGLE_SUBSTITUTION -> data.set(DATA_SUBSTITUTE, if (substituteIngredients) 0 else 1)
            CLEAR_GRID -> (0 until CraftingFrameComponent.GRID_SIZE).forEach { ghosts.setItem(it, ItemStack.EMPTY) }
            else -> return false
        }
        saveFrame()
        return true
    }

    override fun stillValid(player: Player): Boolean = player === inventory.player &&
        (player.level().isClientSide || frameSlot >= 0 && inventory.getItem(frameSlot) === frame &&
            frame.`is`(ItemRegistry.craftingFrame.get()))

    override fun canDragTo(slot: Slot): Boolean = slot.container !== ghosts && slot.containerSlot != lockedSlot

    override fun canTakeItemForPickAll(stack: ItemStack, slot: Slot): Boolean =
        slot.container !== ghosts && slot.containerSlot != lockedSlot && super.canTakeItemForPickAll(stack, slot)

    override fun broadcastChanges() {
        refreshOutput()
        super.broadcastChanges()
    }

    private fun configure(slot: Int, template: ItemStack) {
        ghosts.setItem(slot, if (template.isEmpty) ItemStack.EMPTY else template.copyWithCount(1))
        saveFrame()
    }

    private fun saveFrame() {
        if (inventory.player.level().isClientSide || !stillValid(inventory.player)) return
        frame.set(DataComponentRegistry.craftingFrame.get(), component())
        inventory.setChanged()
        refreshOutput()
        broadcastChanges()
    }

    private fun component(): CraftingFrameComponent = CraftingFrameComponent(
        (0 until CraftingFrameComponent.GRID_SIZE).map(ghosts::getItem), substituteIngredients
    )

    private fun refreshOutput() {
        val level = inventory.player.level() as? ServerLevel ?: return
        val encoded = component()
        val output = encoded.recipe(level)?.value()?.assemble(encoded.input()) ?: ItemStack.EMPTY
        ghosts.setItem(OUTPUT_SLOT, output)
    }

    private inner class GhostSlot(index: Int, x: Int, y: Int) : Slot(ghosts, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = false
        override fun mayPickup(player: Player): Boolean = false
        override fun remove(amount: Int): ItemStack = ItemStack.EMPTY
        override fun isFake(): Boolean = true
    }

    private inner class PlayerSlot(index: Int, x: Int, y: Int) : Slot(inventory, index, x, y) {
        override fun mayPlace(stack: ItemStack): Boolean = containerSlot != lockedSlot
        override fun mayPickup(player: Player): Boolean = containerSlot != lockedSlot
    }

    companion object {
        const val GRID_X = 26
        const val GRID_Y = 17
        const val OUTPUT_X = 134
        const val OUTPUT_Y = 35
        const val PLAYER_Y = 84
        const val TOGGLE_SUBSTITUTION = 0
        const val CLEAR_GRID = 1
        private const val OUTPUT_SLOT = 9
        private const val PLAYER_START = 10
        private const val DATA_SUBSTITUTE = 0
        private const val DATA_FRAME_SLOT = 1
    }
}
