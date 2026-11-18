package com.algorithmlx.ecr.common.machine

import com.algorithmlx.ecr.api.block.entity.syncForNearby
import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.core.NonNullList
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.chat.Component
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

abstract class MachineContainerEntity(
    type: BlockEntityType<*>, pos: BlockPos, state: BlockState, size: Int,
    private val synchronize: Boolean = true, private val hasMenu: Boolean = true
): BaseContainerBlockEntity(type, pos, state), Machine {
    @get:JvmName("getMachineItems")
    @set:JvmName("setMachineItems")
    protected var items: NonNullList<ItemStack> = NonNullList.withSize(size, ItemStack.EMPTY)

    final override fun getItems(): NonNullList<ItemStack> = items
    final override fun setItems(items: NonNullList<ItemStack>) { this.items = items }
    override fun getContainerSize(): Int = items.size
    override fun getDefaultName(): Component = blockState.block.name
    override fun createMenu(containerId: Int, inventory: Inventory, player: Player): AbstractContainerMenu? =
        if (hasMenu) super.createMenu(containerId, inventory, player) else null

    override fun createMenu(containerId: Int, inventory: Inventory): AbstractContainerMenu = error("Missing menu for block entity $type")

    override fun setChanged() {
        super.setChanged()
        if (synchronize) syncForNearby()
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener>? =
        if (synchronize) ClientboundBlockEntityDataPacket.create(this) else super.getUpdatePacket()

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag =
        if (synchronize) saveWithFullMetadata(registries) else super.getUpdateTag(registries)

    protected open fun saveContainerProperties(output: ValueOutput) = super.saveAdditional(output)

    override fun saveAdditional(output: ValueOutput) {
        saveContainerProperties(output)
        saveInventory(output)
    }

    override fun loadAdditional(input: ValueInput) {
        super.loadAdditional(input)
        loadInventory(input)
    }

    protected open fun saveInventory(output: ValueOutput) { ContainerHelper.saveAllItems(output, items) }

    protected open fun loadInventory(input: ValueInput) {
        items.clear()
        ContainerHelper.loadAllItems(input, items)
    }
}
