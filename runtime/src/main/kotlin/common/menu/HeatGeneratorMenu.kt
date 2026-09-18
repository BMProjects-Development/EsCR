package com.algorithmlx.ecr.common.menu

import com.algorithmlx.ecr.api.container.AbstractMenu
import com.algorithmlx.ecr.api.container.slot.VanillaSpecialSlot
import com.algorithmlx.ecr.api.menu.MenuTypeData
import com.algorithmlx.ecr.common.block.entity.HeatGeneratorEntity
import com.algorithmlx.ecr.common.block.entity.HeatGeneratorLogic
import com.algorithmlx.ecr.common.temperature.TemperatureUnit
import com.algorithmlx.ecr.registry.BlockRegistry
import com.algorithmlx.ecr.registry.MenuTypeRegistry
import net.minecraft.core.component.DataComponents
import net.minecraft.world.Container
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.inventory.SimpleContainerData
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BlockEntity

class HeatGeneratorMenu(containerId: Int, private val inventory: Inventory, container: Container, val blockEntity: BlockEntity?, access: ContainerLevelAccess, val data: ContainerData): AbstractMenu(MenuTypeRegistry.instance.heatGenerator, containerId, access) {
    constructor(containerId: Int, inventory: Inventory, typeData: MenuTypeData): this(
        containerId, inventory,
        SimpleContainer(2),
        inventory.player.level().getBlockEntity(typeData.pos),
        ContainerLevelAccess.NULL,
        SimpleContainerData(HeatGeneratorEntity.DATA_COUNT)
    )

    init {
        checkContainerSize(container, 2)
        checkContainerDataCount(data, HeatGeneratorEntity.DATA_COUNT)
        container.startOpen(inventory.player)

        addSlot(VanillaSpecialSlot(container, HeatGeneratorEntity.FUEL_SLOT, FUEL_X, MACHINE_SLOT_Y, { stack -> stack.has(DataComponents.COOKING_FUEL) }))
        addSlot(VanillaSpecialSlot(container, HeatGeneratorEntity.OUTPUT_SLOT, OUTPUT_X, MACHINE_SLOT_Y, { false }))
        inventory.make()
        addDataSlots(data)
    }

    val burnTimeRemaining: Int get() = data.get(HeatGeneratorEntity.DATA_BURN_TIME)
    val maxBurnTime: Int get() = data.get(HeatGeneratorEntity.DATA_MAX_BURN_TIME)
    val temperatureCelsius: Double get() = HeatGeneratorLogic.combineData(
        data.get(HeatGeneratorEntity.DATA_TEMPERATURE_LOW), data.get(HeatGeneratorEntity.DATA_TEMPERATURE_HIGH)
    ) / HeatGeneratorEntity.TEMPERATURE_SCALE
    val generation: Int get() = data.get(HeatGeneratorEntity.DATA_GENERATION)
    val temperatureUnit: TemperatureUnit get() = TemperatureUnit.entries.getOrElse(data.get(HeatGeneratorEntity.DATA_TEMPERATURE_UNIT)) { TemperatureUnit.CELSIUS }
    val mru: Int get() = HeatGeneratorLogic.combineData(data.get(HeatGeneratorEntity.DATA_MRU_LOW), data.get(HeatGeneratorEntity.DATA_MRU_HIGH))
    val mruCapacity: Int get() = HeatGeneratorLogic.combineData(data.get(HeatGeneratorEntity.DATA_MRU_CAPACITY_LOW), data.get(HeatGeneratorEntity.DATA_MRU_CAPACITY_HIGH))
    val isUpgraded: Boolean get() = data.get(HeatGeneratorEntity.DATA_UPGRADED) != 0

    override fun clickMenuButton(player: Player, id: Int): Boolean {
        if (id != CYCLE_TEMPERATURE_UNIT_BUTTON || !isUpgraded || !stillValid(player)) return false
        if (player.level().isClientSide) data.set(HeatGeneratorEntity.DATA_TEMPERATURE_UNIT, temperatureUnit.next().ordinal)
        else (blockEntity as? HeatGeneratorEntity)?.cycleTemperatureUnit() ?: return false
        return true
    }

    override fun quickMoveStack(player: Player, slotIndex: Int): ItemStack {
        val slot = this.slots.getOrNull(slotIndex) ?: return ItemStack.EMPTY
        val stack = slot.item.takeIf { !it.isEmpty } ?: return ItemStack.EMPTY
        val copy = stack.copy()

        val moved = when (slotIndex) {
            HeatGeneratorEntity.FUEL_SLOT, HeatGeneratorEntity.OUTPUT_SLOT -> moveItemStackTo(stack, PLAYER_START, PLAYER_END, true)

            in PLAYER_START ..< PLAYER_END -> {
                if (stack.has(DataComponents.COOKING_FUEL)) moveItemStackTo(
                    stack,
                    HeatGeneratorEntity.FUEL_SLOT,
                    HeatGeneratorEntity.FUEL_SLOT + 1,
                    false
                )
                else if (slotIndex < HOTBAR_START)
                    moveItemStackTo(stack, HOTBAR_START, PLAYER_END, false)
                else moveItemStackTo(stack, PLAYER_START, HOTBAR_START, false)

            }

            else -> false
        }
        if (!moved) return ItemStack.EMPTY

        if (stack.isEmpty) slot.set(ItemStack.EMPTY) else slot.setChanged()
        if (stack.count == copy.count) return ItemStack.EMPTY
        slot.onTake(player, stack)
        return copy
    }

    override fun stillValid(player: Player): Boolean = stillValid(this.access, player, BlockRegistry.instance.heatGenerator)

    override fun removed(player: Player) {
        super.removed(player)
        (this.blockEntity as? Container)?.stopOpen(player)
    }

    companion object {
        const val CYCLE_TEMPERATURE_UNIT_BUTTON = 0
        const val FUEL_X = 44
        const val OUTPUT_X = 116
        const val MACHINE_SLOT_Y = 48

        private const val PLAYER_START = 2
        private const val HOTBAR_START = 29
        private const val PLAYER_END = 38
    }
}
