package com.algorithmlx.ecr.common.block.entity.enrichment

import com.algorithmlx.ecr.api.block.entity.SynchronizedBlockEntity
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.loadMRUData
import com.algorithmlx.ecr.api.mru.saveMRUData
import com.algorithmlx.ecr.api.mru.storage.ExtremeMRUStorageContainer
import com.algorithmlx.ecr.api.mru.storage.IOMRUStorage
import com.algorithmlx.ecr.api.multiblock.MultiblockPlacement
import com.algorithmlx.ecr.common.block.EnrichmentChamberController
import com.algorithmlx.ecr.common.init.config.ECConfig
import com.algorithmlx.ecr.common.menu.EnrichmentChamberControllerMenu
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.BlockRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import com.algorithmlx.ecr.registry.MultiblockRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import net.minecraft.world.phys.AABB

class EnrichmentChamberControllerEntity(worldPosition: BlockPos, blockState: BlockState): SynchronizedBlockEntity(
    BlockEntityTypeRegistry.enrichmentChamberController.get(), worldPosition, blockState
), MRUDevice, MenuProvider {
    private var mutableMRUStorage = ExtremeMRUStorageContainer(0, MRUTypeRegistry.radiationUnit) {
        this.setChanged()
    }
    private var placement: MultiblockPlacement? = null

    override fun loadAdditional(input: ValueInput) {
        placement = null

        val capacity = input.getIntOr(MRU_CAPACITY_TAG, 0).coerceAtLeast(0)
        mutableMRUStorage = mutableMRUStorage.copy(mruCapacity = capacity)
        this.loadMRUData(input)

        if (input.getBooleanOr(HAS_PLACEMENT_TAG, false)) {
            val direction = Direction.entries.getOrNull(input.getIntOr(PLACEMENT_DIRECTION_TAG, -1))
            if (direction != null && direction.axis.isHorizontal) {
                placement = MultiblockPlacement(
                    blockPos,
                    direction,
                    input.getIntOr(PLACEMENT_START_X_TAG, 0),
                    input.getIntOr(PLACEMENT_START_Y_TAG, 0),
                    input.getIntOr(PLACEMENT_START_Z_TAG, 0),
                    input.getIntOr(PLACEMENT_VARIANT_TAG, -1)
                )
            }
        }

        super.loadAdditional(input)
    }

    override fun saveAdditional(output: ValueOutput) {
        output.putInt(MRU_CAPACITY_TAG, mutableMRUStorage.mruCapacity)
        this.saveMRUData(output)

        val placement = placement
        output.putBoolean(HAS_PLACEMENT_TAG, placement != null)
        if (placement != null) {
            output.putInt(PLACEMENT_DIRECTION_TAG, placement.direction.ordinal)
            output.putInt(PLACEMENT_START_X_TAG, placement.startX)
            output.putInt(PLACEMENT_START_Y_TAG, placement.startY)
            output.putInt(PLACEMENT_START_Z_TAG, placement.startZ)
            output.putInt(PLACEMENT_VARIANT_TAG, placement.variantIndex)
        }

        super.saveAdditional(output)
    }

    override val mruStorage: IOMRUStorage get() = mutableMRUStorage
    override val balance = MRUBalanceContainer { setChanged() }
    override val deviceType: MRUDevice.DeviceType = MRUDevice.DeviceType.UNCONNECTABLE

    val innerBounds: AABB? get() {
        val placement = placement ?: return null
        val pattern = multiblock.variants.getOrNull(placement.variantIndex) ?: return null
        if (pattern.xSize <= 2 || pattern.ySize <= 2 || pattern.zSize <= 2) return null

        val first = getWorldPosition(placement, 1, 1, 1)
        val last = getWorldPosition(
            placement,
            pattern.xSize - 2,
            pattern.ySize - 2,
            pattern.zSize - 2
        )

        return AABB(
            minOf(first.x, last.x).toDouble(),
            minOf(first.y, last.y).toDouble(),
            minOf(first.z, last.z).toDouble(),
            maxOf(first.x, last.x) + 1.0,
            maxOf(first.y, last.y) + 1.0,
            maxOf(first.z, last.z) + 1.0
        )
    }

    fun setPlacement(placement: MultiblockPlacement?) {
        if (this.placement == placement) {
            if (placement == null) updateCapacity(0)
            return
        }

        this.placement = placement

        if (placement == null) updateCapacity(0)
        setChanged()
    }

    private inline fun <reified T: EnrichmentChamber> synchronizeElements(level: Level, nextPlacement: MultiblockPlacement?, block: Block) {
        val previousElements = findElements(level, placement, block)
        val nextElements = findElements(level, nextPlacement, block)

        (previousElements - nextElements).forEach { position ->
            val be = level.getBlockEntity(position)

            if (be !is T) return@forEach
            be.disconnectFromController(blockPos)
        }

        nextElements.forEach { position ->
            val be = level.getBlockEntity(position)

            if (be !is T) return@forEach
            be.connectToController(blockPos)
        }
    }

    private fun findElements(level: Level, placement: MultiblockPlacement?, block: Block): Set<BlockPos> {
        if (placement == null) return emptySet()

        return multiblock.matchesIn(
            level, placement, block
        ).asSequence().filter { it.matches }.map { it.worldPos.immutable() }.toSet()
    }

    private fun updateCapacity(capacity: Int) {
        if (capacity == mutableMRUStorage.mruCapacity) return

        val stored = mutableMRUStorage.mru
        mutableMRUStorage = mutableMRUStorage.copy(mruCapacity = capacity)
        mutableMRUStorage.set(stored)
        setChanged()
    }

    override fun getDisplayName(): Component = Component.empty()

    override fun createMenu(
        containerId: Int,
        inventory: Inventory,
        player: Player
    ): AbstractContainerMenu = EnrichmentChamberControllerMenu(
        containerId, inventory, ContainerLevelAccess.create(this.level!!, this.blockPos), this
    )

    companion object {
        private val config = ECConfig.instance.enrichmentChamber
        private val multiblock
            get() = MultiblockRegistry.enrichmentChamber
        private const val MRU_CAPACITY_TAG = "mru_capacity"
        private const val HAS_PLACEMENT_TAG = "has_multiblock_placement"
        private const val PLACEMENT_DIRECTION_TAG = "multiblock_direction"
        private const val PLACEMENT_START_X_TAG = "multiblock_start_x"
        private const val PLACEMENT_START_Y_TAG = "multiblock_start_y"
        private const val PLACEMENT_START_Z_TAG = "multiblock_start_z"
        private const val PLACEMENT_VARIANT_TAG = "multiblock_variant"
        private const val STRUCTURE_CHECK_INTERVAL = 20L

        private fun getWorldPosition(
            placement: MultiblockPlacement,
            x: Int,
            y: Int,
            z: Int
        ): BlockPos {
            val relativeX = x - placement.startX
            val relativeY = y - placement.startY
            val relativeZ = z - placement.startZ

            return when (placement.direction) {
                Direction.NORTH -> placement.basePos.offset(relativeX, relativeY, -relativeZ)
                Direction.SOUTH -> placement.basePos.offset(-relativeX, relativeY, relativeZ)
                Direction.WEST -> placement.basePos.offset(-relativeZ, relativeY, -relativeX)
                Direction.EAST -> placement.basePos.offset(relativeZ, relativeY, relativeX)
                else -> placement.basePos
            }
        }

        @JvmStatic
        fun onTick(level: Level, state: BlockState, be: EnrichmentChamberControllerEntity) {
            if (level.isClientSide) return
            if (
                level.gameTime % STRUCTURE_CHECK_INTERVAL !=
                Math.floorMod(be.blockPos.asLong(), STRUCTURE_CHECK_INTERVAL)
            ) return

            var placement = EnrichmentChamberController.findPlacement(level, be.blockPos, state)

            if (placement != null && MultiblockRegistry.enrichmentChamber.countMatchesIn(level, placement, BlockRegistry.enrichmentChamberController.get()) > 1)
                placement = null

            be.synchronizeElements<EnrichmentChamberExtractorEntity>(
                level,
                placement,
                BlockRegistry.enrichmentChamberExtractor.get()
            )
            be.synchronizeElements<EnrichmentChamberReceiverEntity>(
                level,
                placement,
                BlockRegistry.enrichmentChamberReceiver.get()
            )
            be.setPlacement(placement)

            val active = placement != null
            if (state.getValue(EnrichmentChamberController.ACTIVE) != active) {
                level.setBlockAndUpdate(
                    be.blockPos,
                    state.setValue(EnrichmentChamberController.ACTIVE, active)
                )
            }

            if (placement == null) return

            val holderCount = multiblock.countMatchesIn(
                level,
                placement,
                BlockRegistry.enrichmentChamberHolder.get()
            )
            val capacity = (
                config.controllerCapacity.toLong() +
                    config.holderCapacity.toLong() * holderCount
                ).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            be.updateCapacity(capacity)
        }
    }
}
