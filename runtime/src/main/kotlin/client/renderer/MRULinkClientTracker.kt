package com.algorithmlx.ecr.client.renderer

import com.algorithmlx.ecr.api.mru.MRUDevice
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.block.entity.BlockEntity
import java.util.*

object MRULinkClientTracker {
    private val devices: MutableSet<BlockEntity> = Collections.newSetFromMap(IdentityHashMap())

    @JvmStatic
    fun register(blockEntity: BlockEntity) {
        val level = blockEntity.level ?: return

        if (!level.isClientSide || blockEntity !is MRUDevice) return

        devices += blockEntity
    }

    @JvmStatic
    fun unregister(blockEntity: BlockEntity) {
        devices -= blockEntity
    }

    @JvmStatic
    fun clear() {
        devices.clear()
    }

    @JvmStatic
    fun snapshot(level: ClientLevel): List<BlockEntity> {
        if (devices.isEmpty()) return emptyList()

        val result = ArrayList<BlockEntity>(devices.size)
        val iterator = devices.iterator()

        while (iterator.hasNext()) {
            val blockEntity = iterator.next()
            val entityLevel = blockEntity.level
            if (blockEntity.isRemoved || entityLevel == null) {
                iterator.remove()
                continue
            }

            if (entityLevel === level && blockEntity is MRUDevice)
                result += blockEntity
        }

        return result
    }
}
