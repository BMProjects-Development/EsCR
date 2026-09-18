@file:JvmName("MRUDeviceHelper")

package com.algorithmlx.ecr.api.mru

import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblocks
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.mru.balance.MutableMRUBalance
import com.algorithmlx.ecr.api.mru.storage.IOMRUStorage
import net.minecraft.core.BlockPos
import net.minecraft.world.Container
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import kotlin.jvm.optionals.getOrNull

interface MRUDevice {
    val mruStorage: IOMRUStorage

    val balance: MutableMRUBalance

    val locator: LocatorData? get() = null

    val deviceType: DeviceType

    enum class DeviceType {
        RECEIVER,

        TRANSLATOR,

        IO,

        CONNECTABLE_RECEIVER,

        UNCONNECTABLE,

        ;

        val isExporter: Boolean get() = this == TRANSLATOR || this.isUniversal

        val isUniversal: Boolean get() = this == IO

        val isReceiver: Boolean get() = this == RECEIVER || this == CONNECTABLE_RECEIVER || this.isUniversal

        val isConnectable: Boolean get() = this == CONNECTABLE_RECEIVER || this.isExporter
    }

    data class LocatorData(
        val locatorStorage: Container,
        val locatorSlot: Int,
        val position: BlockPos? = (locatorStorage as? BlockEntity)?.blockPos?.immutable(),
    )
}

fun Level.resolveMRUDevice(pos: BlockPos): MRUDevice? {
    val direct = getBlockEntity(pos)
    if (direct is MRUDevice) return direct
    return AssembledMultiblocks.controllerBlockEntity(this, pos) as? MRUDevice
}

fun MRUDevice.saveMRUData(output: ValueOutput) {
    mruStorage.save(output)
    balance.save(output.child(BALANCE_TAG))
}

fun MRUDevice.loadMRUData(input: ValueInput) {
    mruStorage.load(input)
    input.child(BALANCE_TAG).getOrNull()?.let(balance::load)
}

fun MRUDevice.processReceive(level: Level) {
    if (level.isClientSide) return

    val locatorData = this.locator ?: return
    val stack = locatorData.locatorStorage.getItem(locatorData.locatorSlot)
    val item = stack.item as? BoundGem ?: return

    val pos = item.getBoundPos(stack) ?: return
    val server = level.server ?: return
    val world = item.getWorld(stack)

    val logicalLevel = world?.let { server.getLevel(it) } ?: level
    val outsideRadius = locatorData.position?.let { receiverPos ->
        logicalLevel !== level || !item.isWithinBoundRadius(receiverPos, pos)
    } ?: false
    if (item.setOutsideBoundRadius(stack, outsideRadius))
        locatorData.locatorStorage.setChanged()
    if (outsideRadius) return

    val exporter = logicalLevel.resolveMRUDevice(pos) ?: return

    if (!exporter.deviceType.isExporter || !this.deviceType.isReceiver) return

    val currentContainer = this.mruStorage
    val generator = exporter.mruStorage

    if (
        exporter === this || generator === currentContainer
        || exporter.balance === this.balance
        || !generator.isSameTypes(currentContainer)
    ) return

    this.balance.includeSource(exporter.balance, level.gameTime)
    generator.transferTo(currentContainer, item.transferStrength)
}

private const val BALANCE_TAG = "balance"
