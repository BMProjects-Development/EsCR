package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.api.geo.GeoAnimatable
import com.algorithmlx.ecr.api.geo.GeoAnimationState
import com.algorithmlx.ecr.api.geo.GeoModel
import com.algorithmlx.ecr.api.molang.runtime.BlockEntityQuery
import com.algorithmlx.ecr.api.molang.runtime.MolangContext
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDataIO
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockPartData
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockPartEntity
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.protocol.Packet
import net.minecraft.network.protocol.game.ClientGamePacketListener
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class AssembledMultiblockPartBlockEntity(
    worldPosition: BlockPos,
    blockState: BlockState
): BlockEntity(
    BlockEntityTypeRegistry.assembledMultiblockPart.get(),
    worldPosition,
    blockState
), AssembledMultiblockPartEntity, GeoAnimatable {
    override var assembledMultiblockData: AssembledMultiblockPartData? = null
        private set

    override val geoAnimationState = GeoAnimationState()
    private val geoQuery = BlockEntityQuery(this)

    override val geoModel: GeoModel
        get() {
            val definitionId = assembledMultiblockData?.definitionId
                ?: error("Assembled multiblock GEO model requested before part data was loaded")
            return MultiblockDefinitions.assembled(definitionId)?.formedModel
                ?: error("Assembled multiblock $definitionId has no formed GEO model")
        }

    override fun geoMolangContext(partialTick: Float): MolangContext = MolangContext(geoQuery)

    override fun setAssembledMultiblockData(data: AssembledMultiblockPartData) {
        require(data.original.position == blockPos) {
            "Assembled multiblock part data belongs to ${data.original.position}, not $blockPos"
        }
        assembledMultiblockData = data
        setChanged()
        level?.takeUnless { it.isClientSide }?.sendBlockUpdated(
            blockPos,
            blockState,
            blockState,
            Block.UPDATE_CLIENTS
        )
    }

    override fun clearAssembledMultiblockData() {
        if (assembledMultiblockData == null) return
        assembledMultiblockData = null
        setChanged()
        level?.takeUnless { it.isClientSide }?.sendBlockUpdated(
            blockPos,
            blockState,
            blockState,
            Block.UPDATE_CLIENTS
        )
    }

    override fun getUpdatePacket(): Packet<ClientGamePacketListener> = ClientboundBlockEntityDataPacket.create(this)

    override fun getUpdateTag(registries: net.minecraft.core.HolderLookup.Provider): CompoundTag =
        saveWithoutMetadata(registries)

    override fun saveAdditional(output: ValueOutput) {
        AssembledMultiblockDataIO.write(output, assembledMultiblockData)
        super.saveAdditional(output)
    }

    override fun loadAdditional(input: ValueInput) {
        assembledMultiblockData = AssembledMultiblockDataIO.read(input)
        super.loadAdditional(input)
    }
}
