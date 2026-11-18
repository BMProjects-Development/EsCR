package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.common.machine.MachineContainerEntity
import com.algorithmlx.ecr.api.chunk.ChunkLoadingManager
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.loadMRUData
import com.algorithmlx.ecr.api.mru.processReceive
import com.algorithmlx.ecr.api.mru.saveMRUData
import com.algorithmlx.ecr.api.mru.storage.IOMRUStorage
import com.algorithmlx.ecr.api.mru.storage.MRUStorageContainer
import com.algorithmlx.ecr.api.particle.BedrockParticles
import com.algorithmlx.ecr.api.particle.ClientParticleSystems
import com.algorithmlx.ecr.api.particle.ParticleEmitter
import com.algorithmlx.ecr.api.particle.Transform
import com.algorithmlx.ecr.api.utils.ecPrefix
import com.algorithmlx.ecr.common.api.BoundGemHelper
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.common.menu.MagicalTeleporterMenu
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import com.algorithmlx.ecr.registry.MultiblockRegistry
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import org.joml.Quaternionf
import org.joml.Vector3f

class MagicalTeleporterEntity(
    worldPosition: BlockPos,
    blockState: BlockState
): MachineContainerEntity(BlockEntityTypeRegistry.magicalTeleporter.get(), worldPosition, blockState, 2),
    MRUDevice {
    override fun clientTick(level: Level) {
        updateStructure(level)
        val entityAtTeleporter = level.getNearestPlayer(blockPos.x + 0.5, blockPos.y + 1.0, blockPos.z + 0.5, 0.5, false)
        val system = ClientParticleSystems.system(level)
        val activeId = "${ECRModIDs.MAGICAL_TELEPORTER}/active".ecPrefix
        val hasPlayerId = "${ECRModIDs.MAGICAL_TELEPORTER}/teleport".ecPrefix

        if (snowstormEmitters.isEmpty() && structureIsValid) {
            val active = BedrockParticles[activeId] ?: return
            snowstormEmitters += system.spawn(active, transform = snowstormTransform)
        } else if (!structureIsValid && snowstormEmitters.isNotEmpty()) {
            snowstormEmitters.forEach { it.stopLoop() }
            snowstormEmitters.clear()
        }

        if (entityAtTeleporter != null && structureIsValid && !playerParticleSpawned) {
            val hasPlayer = BedrockParticles[hasPlayerId] ?: return
            snowstormEmitters += system.spawn(hasPlayer, transform = snowstormTransform)
            playerParticleSpawned = true
        } else if (entityAtTeleporter == null && playerParticleSpawned) {
            playerParticleSpawned = false
            snowstormEmitters.filter { it.effect.identifier == hasPlayerId }.forEach { it.stopLoop() }
            snowstormEmitters.removeIf { it.effect.identifier == hasPlayerId }
        }
    }

    override fun serverTick(level: ServerLevel) {
        updateStructure(level)
        val entityAtTeleporter = level.getNearestPlayer(blockPos.x + 0.5, blockPos.y + 1.0, blockPos.z + 0.5, 0.5, false)

        if (!structureIsValid) {
            resetProgress()
            if (isChunkLoaded) {
                isChunkLoaded = false
                ChunkLoadingManager.remove(level, blockPos)
            }
            return
        }

        if (!isChunkLoaded) {
            isChunkLoaded = true
            ChunkLoadingManager.update(level, blockPos, 0)
        }

        processReceive(level)

        val stack = getItem(1)
        if (stack.item !is BoundGem) return

        val destPos = BoundGemHelper.getBoundPos(stack) ?: return
        val levelKey = BoundGemHelper.getLevelKey(stack)

        val dimensionalLevel = levelKey?.let { level.server.getLevel(it) } ?: level

        if (blockPos == destPos) {
            resetProgress()
            return
        }

        if (entityAtTeleporter == null) {
            if (progressTime > 0) resetProgress()
            return
        }

        val destOpt = dimensionalLevel.getBlockEntity(destPos, BlockEntityTypeRegistry.magicalTeleporter.get())
        if (!destOpt.isPresent) {
            resetProgress()
            return
        }

        val dest = destOpt.get()
        if (!dest.structureIsValid) return

        if (mruStorage.canExtract(config.mruUsage)) {
            mruStorage.extract(config.mruUsage)
            progressTime++
            setChanged()
        }

        if (progressTime < config.ticksRequired) return

        entityAtTeleporter.teleportTo(
            dimensionalLevel,
            dest.blockPos.x + 0.5,
            dest.blockPos.y + 1.0,
            dest.blockPos.z + 0.5,
            setOf(),
            entityAtTeleporter.yRot,
            entityAtTeleporter.xRot,
            false
        )

        resetProgress()
    }

    private fun updateStructure(level: Level) {
        val valid = hasValidStructure(level, blockPos)
        if (structureIsValid == valid) return
        structureIsValid = valid
        setChanged()
    }

    private var progressTime = 0
    private var structureIsValid = false
    private var isChunkLoaded = false
    private var playerParticleSpawned = false
    private val snowstormEmitters = mutableListOf<ParticleEmitter>()
    private val snowstormTransform =
        object: Transform {
            override val parent: Transform? = null
            override val isValid: Boolean get() = !isRemoved
            override val position: Vector3f
                get() = Vector3f(blockPos.x + 0.5F, blockPos.y + 0.15F, blockPos.z + 0.5F)
            override val rotation: Quaternionf get() = Quaternionf()
            override val velocity: Vector3f get() = Vector3f()
        }

    override fun getDefaultName(): Component = Component.empty()

    override fun createMenu(containerId: Int, inventory: Inventory): AbstractContainerMenu =
        MagicalTeleporterMenu(containerId, inventory, this, this, ContainerLevelAccess.create(this.level!!, this.blockPos))

    override fun saveAdditional(output: ValueOutput) {

        output.putInt("progress", this.progressTime)
        output.putBoolean("structure_valid", this.structureIsValid)

        saveMRUData(output)
        super.saveAdditional(output)
    }

    override fun loadAdditional(input: ValueInput) {

        this.progressTime = input.getIntOr("progress", 0)
        this.structureIsValid = input.getBooleanOr("structure_valid", false)

        loadMRUData(input)
        super.loadAdditional(input)
    }

    override val mruStorage: IOMRUStorage = MRUStorageContainer(50000, MRUTypeRegistry.radiationUnit) { this.setChanged() }
    override val balance = MRUBalanceContainer { setChanged() }
    override val deviceType: MRUDevice.DeviceType = MRUDevice.DeviceType.CONNECTABLE_RECEIVER
    override val locator: MRUDevice.LocatorData = MRUDevice.LocatorData(this, 0)

    override fun preRemoveSideEffects(pos: BlockPos, state: BlockState) {
        if (isChunkLoaded) {
            (level as? ServerLevel)?.let {
                ChunkLoadingManager.remove(it, pos)
            }
            this.isChunkLoaded = false
        }

        super.preRemoveSideEffects(pos, state)
    }

    companion object {
        private val config = ECConfig.current.magicalTeleporter

        @JvmStatic
        fun hasValidStructure(level: Level, pos: BlockPos): Boolean = MultiblockRegistry.magicalTeleporter.findPlacement(level, pos, BlockPos(2, 0, 2)) != null

        private fun MagicalTeleporterEntity.resetProgress() {
            this.progressTime = 0
            this.setChanged()
        }
    }
}
