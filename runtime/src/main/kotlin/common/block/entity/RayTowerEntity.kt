package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.api.block.entity.SynchronizedContainerBlockEntity
import com.algorithmlx.ecr.api.chunk.ChunkLoadingManager
import com.algorithmlx.ecr.api.geo.GeoAnimatable
import com.algorithmlx.ecr.api.geo.GeoAnimationState
import com.algorithmlx.ecr.api.geo.GeoModel
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.molang.runtime.BlockEntityQuery
import com.algorithmlx.ecr.api.molang.runtime.MolangContext
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.loadMRUData
import com.algorithmlx.ecr.api.mru.processReceive
import com.algorithmlx.ecr.api.mru.saveMRUData
import com.algorithmlx.ecr.api.mru.storage.IOMRUStorage
import com.algorithmlx.ecr.api.mru.storage.MRUStorageContainer
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDataIO
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockPartData
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockPartEntity
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblocks
import com.algorithmlx.ecr.common.api.BoundGemHelper
import com.algorithmlx.ecr.common.menu.RayTowerMenu
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.Container
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class RayTowerEntity(
    worldPosition: BlockPos,
    blockState: BlockState
): SynchronizedContainerBlockEntity(
    BlockEntityTypeRegistry.rayTower.get(), worldPosition, blockState
), MRUDevice, AssembledMultiblockPartEntity, GeoAnimatable {
    override var assembledMultiblockData: AssembledMultiblockPartData? = null
        private set
    override val geoAnimationState = GeoAnimationState()
    private val geoQuery = BlockEntityQuery(this)
    private var items = NonNullList.withSize(1, ItemStack.EMPTY)
    private var isChunkLoaded: Boolean = false

    override val geoModel: GeoModel
        get() {
            val definitionId = assembledMultiblockData?.definitionId
                ?: error("Ray tower GEO model requested before assembly")
            return MultiblockDefinitions.assembled(definitionId)?.formedModel
                ?: error("Assembled multiblock $definitionId has no formed GEO model")
        }

    override fun geoMolangContext(partialTick: Float): MolangContext = MolangContext(geoQuery)

    override fun getDefaultName(): Component = Component.empty()

    override fun getItems(): NonNullList<ItemStack> = this.items

    override fun setItems(items: NonNullList<ItemStack>) {
        this.items = items
    }

    override fun createMenu(containerId: Int, inventory: Inventory): AbstractContainerMenu = RayTowerMenu(
        containerId, inventory, this, this,
        ContainerLevelAccess.create(this.level!!, this.blockPos)
    )

    override fun getContainerSize(): Int = this.items.size

    override fun canPlaceItem(slot: Int, stack: ItemStack): Boolean =
        isAssembledMultiblock && super.canPlaceItem(slot, stack)

    override fun canTakeItem(target: Container, slot: Int, stack: ItemStack): Boolean =
        isAssembledMultiblock && super.canTakeItem(target, slot, stack)

    override fun saveAdditional(output: ValueOutput) {
        ContainerHelper.saveAllItems(output, this.items)
        this.saveMRUData(output)
        AssembledMultiblockDataIO.write(output, assembledMultiblockData)
        super.saveAdditional(output)
    }

    override fun loadAdditional(input: ValueInput) {
        ContainerHelper.loadAllItems(input, this.items)
        this.loadMRUData(input)
        assembledMultiblockData = AssembledMultiblockDataIO.read(input)
        super.loadAdditional(input)
    }

    override fun setAssembledMultiblockData(data: AssembledMultiblockPartData) {
        if (data.original.position != blockPos) return
        assembledMultiblockData = data
        setChanged()
    }

    override fun clearAssembledMultiblockData() {
        if (assembledMultiblockData == null) return
        assembledMultiblockData = null
        geoAnimationState.clear()
        setChanged()
    }

    override val mruStorage: IOMRUStorage = MRUStorageContainer(5000, MRUTypeRegistry.radiationUnit) {
        this.setChanged()
    }
    override val balance = MRUBalanceContainer { setChanged() }

    override val deviceType: MRUDevice.DeviceType
        get() = if (isAssembledMultiblock) MRUDevice.DeviceType.IO else MRUDevice.DeviceType.UNCONNECTABLE

    override val locator: MRUDevice.LocatorData?
        get() = if (isAssembledMultiblock) MRUDevice.LocatorData(this, 0) else null

    override fun preRemoveSideEffects(pos: BlockPos, state: BlockState) {
        if (this.isChunkLoaded) {
            (level as? ServerLevel)?.let {
                ChunkLoadingManager.remove(it, pos)
            }
            this.isChunkLoaded = false
        }

        super.preRemoveSideEffects(pos, state)
    }

    companion object {
        @JvmStatic
        fun onTick(level: Level, blockPos: BlockPos, be: RayTowerEntity) {
            AssembledMultiblocks.tick(level, blockPos)

            val serverLevel = level as? ServerLevel ?: return

            if (!be.isAssembledMultiblock) {
                if (be.isChunkLoaded) {
                    ChunkLoadingManager.remove(serverLevel, blockPos)
                    be.isChunkLoaded = false
                }
                return
            }

            if (!be.isChunkLoaded) {
                ChunkLoadingManager.update(serverLevel, blockPos, 0)
                be.isChunkLoaded = true
            }

            val itemStack = be.getItem(0)
            if (itemStack.item !is BoundGem) return

            val pos = BoundGemHelper.getBoundPos(itemStack)

            if (pos != null && level.getBlockEntity(pos) != null && level.getBlockEntity(pos) == level.getBlockEntity(blockPos)) return

            be.processReceive(level)
        }
    }
}
