package com.algorithmlx.ecr.api.block.entity

import net.minecraft.core.BlockPos
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState

abstract class SynchronizedContainerBlockEntity(
    type: BlockEntityType<*>, worldPosition: BlockPos, blockState: BlockState
): BaseContainerBlockEntity(type, worldPosition, blockState) {
    override fun setChanged() {
        super.setChanged()
        this.syncForNearby()
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(registries: HolderLookup.Provider): CompoundTag = this.saveWithFullMetadata(registries)
}
