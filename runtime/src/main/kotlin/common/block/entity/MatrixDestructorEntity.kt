package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.api.block.entity.SynchronizedContainerBlockEntity
import com.algorithmlx.ecr.api.item.SoulStoneLike
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.loadMRUData
import com.algorithmlx.ecr.api.mru.saveMRUData
import com.algorithmlx.ecr.api.mru.storage.IOMRUStorage
import com.algorithmlx.ecr.api.mru.storage.MRUStorageContainer
import com.algorithmlx.ecr.common.components.playerMatrix
import com.algorithmlx.ecr.common.components.updatePlayerMatrix
import com.algorithmlx.ecr.common.init.config.ECConfig
import com.algorithmlx.ecr.common.menu.MatrixDestructorMenu
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.DataComponentRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import kotlin.jvm.optionals.getOrElse

class MatrixDestructorEntity(
    worldPosition: BlockPos, blockState: BlockState
): SynchronizedContainerBlockEntity(BlockEntityTypeRegistry.matrixDestructor.get(), worldPosition, blockState), MRUDevice {
    private var items: NonNullList<ItemStack> = NonNullList.withSize(1, ItemStack.EMPTY)

    var progress = 0
    @JvmField
    var status = MatrixDestructorStatus.STOPPED

    override fun getDefaultName(): Component = Component.empty()

    override fun getItems(): NonNullList<ItemStack> = this.items

    override fun setItems(items: NonNullList<ItemStack>) {
        this.items = items
    }

    override fun saveAdditional(output: ValueOutput) {
        ContainerHelper.saveAllItems(output, this.items)
        output.store("status", MatrixDestructorStatus.CODEC, this.status)
        output.putInt("progress", this.progress)
        this.saveMRUData(output)
        super.saveAdditional(output)
    }

    override fun loadAdditional(input: ValueInput) {
        this.items = NonNullList.withSize(this.containerSize, ItemStack.EMPTY)
        ContainerHelper.loadAllItems(input, this.items)
        this.status = input.read("status", MatrixDestructorStatus.CODEC).getOrElse { MatrixDestructorStatus.STOPPED }
        this.progress = input.getIntOr("progress", 0)
        this.loadMRUData(input)
        super.loadAdditional(input)
    }

    override fun createMenu(
        containerId: Int,
        inventory: Inventory
    ): AbstractContainerMenu = MatrixDestructorMenu(
        containerId, inventory, this, this,
        ContainerLevelAccess.create(this.level!!, this.blockPos)
    )

    override fun getContainerSize(): Int = this.items.size

    override val mruStorage: IOMRUStorage = MRUStorageContainer(10000, MRUTypeRegistry.radiationUnit) { setChanged() }
    override val balance = MRUBalanceContainer(initialUpperBalance = config.balanceProduced, initialLowerBalance = config.balanceProduced) { setChanged() }
    override val deviceType: MRUDevice.DeviceType = MRUDevice.DeviceType.TRANSLATOR

    fun setStatusUpdated(status: MatrixDestructorStatus) {
        if (this.status == status) return
        this.status = status
        this.setChanged()
    }

    enum class MatrixDestructorStatus {
        WAITING, WORKING, WARNING, STOPPED;

        companion object {
            @JvmField
            val CODEC: Codec<MatrixDestructorStatus> = RecordCodecBuilder.create {
                it.group(
                    Codec.STRING.fieldOf("name").forGetter(MatrixDestructorStatus::name)
                ).apply(it, MatrixDestructorStatus::valueOf)
            }
        }
    }

    companion object {
        private val config get() = ECConfig.current.matrixDestructor

        @JvmStatic
        fun onTick(level: Level, be: MatrixDestructorEntity) {
            if (level.isClientSide) return
            be.balance.setBalance(config.balanceProduced, config.balanceProduced)

            val serverLevel = level as ServerLevel
            val stack = be.getItem(0)

            if (stack.isEmpty) {
                be.progress = 0
                be.setStatusUpdated(
                    if (be.mruStorage.hasMRU) MatrixDestructorStatus.WAITING
                    else MatrixDestructorStatus.STOPPED
                )
                return
            }

            val soulStoneComponent = stack.get(DataComponentRegistry.soulStone.get())
            if (soulStoneComponent == null) {
                be.progress = 0
                be.setStatusUpdated(MatrixDestructorStatus.STOPPED)
                return
            }

            val owner = serverLevel.server.playerList.getPlayer(soulStoneComponent.owner) ?: return
            val matrix = owner.playerMatrix

            val capacity = matrix.mru
            val i = stack.item as? SoulStoneLike
            if (i == null) {
                be.progress = 0
                be.setStatusUpdated(MatrixDestructorStatus.STOPPED)
                return
            }

            val convertCost = i.extractCount
            val receiveCost = i.receiveCount
            val hasEnoughStorage = capacity >= receiveCost
            val hasPower = capacity > 0

            when {
                be.mruStorage.isFilled && hasPower -> {
                    be.progress = 0
                    be.setStatusUpdated(MatrixDestructorStatus.WARNING)
                    return
                }

                !hasPower || !hasEnoughStorage || capacity - convertCost < 0 -> {
                    be.progress = 0
                    be.setStatusUpdated(MatrixDestructorStatus.STOPPED)
                    return
                }
            }

            be.setStatusUpdated(MatrixDestructorStatus.WORKING)
            owner.updatePlayerMatrix { extract(convertCost) }
            be.progress += convertCost

            if (be.progress >= convertCost) {
                be.progress = 0
                be.mruStorage.insert(receiveCost)
                be.setChanged()
            }
        }
    }
}
