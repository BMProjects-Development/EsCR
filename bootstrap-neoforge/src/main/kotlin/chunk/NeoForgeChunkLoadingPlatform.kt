package com.algorithmlx.ecr.neoforge.chunk

import com.algorithmlx.ecr.api.chunk.ChunkLoadingPlatform
import com.algorithmlx.ecr.api.utils.ecRL
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent
import net.neoforged.neoforge.common.world.chunk.TicketController
import net.neoforged.neoforge.common.world.chunk.TicketHelper
import java.util.WeakHashMap

class NeoForgeChunkLoadingPlatform(bus: IEventBus) : ChunkLoadingPlatform {
    private val owners = WeakHashMap<ServerLevel, MutableMap<BlockPos, MutableSet<Long>>>()

    private val controller = TicketController(
        "chunk_loader".ecRL,
        ::restoreOwners
    )

    init {
        bus.addListener(::registerController)
    }

    override fun add(level: ServerLevel, owner: BlockPos, chunk: ChunkPos): Boolean {
        val ownerChunks = owners
            .getOrPut(level, ::HashMap)
            .getOrPut(owner.immutable(), ::HashSet)

        val ownerChanged = ownerChunks.add(chunk.pack())
        val ticketChanged = controller.forceChunk(
            level, owner, chunk.x, chunk.z, true, false
        )

        return ownerChanged || ticketChanged
    }

    override fun remove(level: ServerLevel, owner: BlockPos, chunk: ChunkPos): Boolean {
        val ownerKey = owner.immutable()
        val levelOwners = owners[level]
        val ownerChunks = levelOwners?.get(ownerKey)
        val ownerChanged = ownerChunks?.remove(chunk.pack()) == true

        if (ownerChunks?.isEmpty() == true) levelOwners.remove(ownerKey)
        if (levelOwners?.isEmpty() == true) owners.remove(level)

        val ticketChanged = controller.forceChunk(
            level,
            owner,
            chunk.x,
            chunk.z,
            false,
            false
        )

        return ownerChanged || ticketChanged
    }

    override fun removeAll(level: ServerLevel, owner: BlockPos) {
        val ownerKey = owner.immutable()
        val levelOwners = owners[level] ?: return
        val chunks = levelOwners.remove(ownerKey)?.toList() ?: return

        for (packed in chunks) {
            val chunk = ChunkPos.unpack(packed)
            controller.forceChunk(
                level,
                owner,
                chunk.x,
                chunk.z,
                false,
                false
            )
        }

        if (levelOwners.isEmpty()) owners.remove(level)
    }

    private fun registerController(event: RegisterTicketControllersEvent) {
        event.register(controller)
    }

    private fun restoreOwners(level: ServerLevel, helper: TicketHelper) {
        val levelOwners = HashMap<BlockPos, MutableSet<Long>>()

        for ((owner, tickets) in helper.blockTickets) {
            val chunks = HashSet<Long>()

            val normal = tickets.normal().iterator()
            while (normal.hasNext()) {
                chunks.add(normal.nextLong())
            }

            val naturalSpawning = tickets.naturalSpawning().iterator()
            while (naturalSpawning.hasNext()) {
                chunks.add(naturalSpawning.nextLong())
            }

            if (chunks.isNotEmpty()) levelOwners[owner.immutable()] = chunks
        }

        if (levelOwners.isEmpty()) owners.remove(level)
        else owners[level] = levelOwners
    }
}
