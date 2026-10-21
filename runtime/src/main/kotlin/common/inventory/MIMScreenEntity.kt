package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.api.mim.MIMTerminal
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.common.menu.MIMMenuKind
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.state.BlockState

class MIMScreenEntity(pos: BlockPos, state: BlockState) : MIMModuleEntity(
    pos, state, BlockEntityTypeRegistry.mimScreen.get(), MIMMenuKind.SCREEN.slots
), MIMTerminal {
    override val menuKind get() = MIMMenuKind.SCREEN
    override val previousInventorySize: Int get() = 2
    override val deviceType get() = MRUDevice.DeviceType.UNCONNECTABLE

    override val canWithdraw: Boolean
        get() = parent()?.power?.let { it.mru >= ECConfig.current.inventoryManager.screenExtractCost } == true

    override fun deposit(stack: ItemStack): ItemStack {
        if (stack.isEmpty || level?.isClientSide != false) return stack.copy()
        val core = parent() ?: return stack.copy()
        val cost = ECConfig.current.inventoryManager.screenInsertCost
        if (core.power.mru < cost) return stack.copy()
        val remainder = core.network().insert(stack.copy())
        if (remainder.count < stack.count) core.power.extract(cost)
        return remainder
    }

    override fun withdraw(template: ItemStack, amount: Int): ItemStack {
        if (template.isEmpty || amount <= 0 || level?.isClientSide != false) return ItemStack.EMPTY
        val core = parent() ?: return ItemStack.EMPTY
        val cost = ECConfig.current.inventoryManager.screenExtractCost
        val affordable = if (cost == 0) amount else minOf(amount, core.power.mru / cost)
        if (affordable <= 0) return ItemStack.EMPTY
        val result = core.network().extract(template, minOf(affordable, template.maxStackSize))
        if (!result.isEmpty) core.power.extract(result.count * cost)
        return result
    }
}
