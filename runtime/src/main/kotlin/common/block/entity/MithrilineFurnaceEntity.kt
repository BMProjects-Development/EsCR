package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.api.block.entity.SynchronizedContainerBlockEntity
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.loadMRUData
import com.algorithmlx.ecr.api.mru.saveMRUData
import com.algorithmlx.ecr.api.mru.storage.MRUStorageContainer
import com.algorithmlx.ecr.api.particle.BedrockParticles
import com.algorithmlx.ecr.api.particle.ClientParticleSystems
import com.algorithmlx.ecr.api.particle.ParticleEmitter
import com.algorithmlx.ecr.api.particle.Transform
import com.algorithmlx.ecr.api.recipe.CachedRecipe
import com.algorithmlx.ecr.api.utils.StackHelper
import com.algorithmlx.ecr.api.utils.count
import com.algorithmlx.ecr.api.utils.ecPrefix
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.common.menu.MithrilineFurnaceMenu
import com.algorithmlx.ecr.registry.*
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.world.ContainerHelper
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.floor

class MithrilineFurnaceEntity(
    worldPosition: BlockPos,
    blockState: BlockState
): SynchronizedContainerBlockEntity(BlockEntityTypeRegistry.mithrilineFurnace.get(), worldPosition, blockState), MRUDevice, WorldlyContainer {
    @all:JvmName("items")
    private var items = NonNullList.withSize(2, ItemStack.EMPTY)
    private val containerData: ContainerData = object : ContainerData {
        override fun get(index: Int): Int = when (index) {
            0 -> this@MithrilineFurnaceEntity.craftProgress
            1 -> this@MithrilineFurnaceEntity.maxCraftProgress
            else -> 0
        }

        override fun set(index: Int, value: Int) {
            when (index) {
                0 -> this@MithrilineFurnaceEntity.craftProgress = value
                1 -> this@MithrilineFurnaceEntity.maxCraftProgress = value
            }
        }

        override fun getCount(): Int = 2
    }

    val recipe = CachedRecipe(RecipeTypeRegistry.mithrilineFurnace.get())

    var structureIsValid = false
    var craftProgress = 0
    var maxCraftProgress = 0
    var slownessGeneration = false
    var espeGenerationRemainder = 0.0
    // Client Only
    var coreRotationPrevious = 0f
    var coreRotationAngle = 0f
    private val snowstormEmitters = mutableListOf<ParticleEmitter>()
    private val snowstormTransform = object : Transform {
        override val parent: Transform? = null
        override val isValid: Boolean get() = !isRemoved
        override val position: Vector3f
            get() = Vector3f(blockPos.x + 0.5F, blockPos.y + 0.15F, blockPos.z + 0.5F)
        override val rotation: Quaternionf get() = Quaternionf()
        override val velocity: Vector3f get() = Vector3f()
    }

    override fun saveAdditional(output: ValueOutput) {
        ContainerHelper.saveAllItems(output, this.items)
        output.putBoolean("structure_valid", structureIsValid)
        output.putBoolean("slow_generation", this.slownessGeneration)
        output.putInt("progress", this.craftProgress)
        output.putInt("max_progress", this.maxCraftProgress)
        output.putDouble("espe_fraction", this.espeGenerationRemainder)
        saveMRUData(output)
        super.saveAdditional(output)
    }

    override fun loadAdditional(input: ValueInput) {
        ContainerHelper.loadAllItems(input, this.items)
        structureIsValid = input.getBooleanOr("structure_valid", false)
        slownessGeneration = input.getBooleanOr("slow_generation", false)
        craftProgress = input.getIntOr("progress", 0)
        maxCraftProgress = input.getIntOr("max_progress", 0)
        espeGenerationRemainder = input.getDoubleOr("espe_fraction", 0.0)
        loadMRUData(input)
        super.loadAdditional(input)
    }

    override fun getDefaultName(): Component = Component.empty()

    override fun getItems(): NonNullList<ItemStack> = this.items

    override fun setItems(items: NonNullList<ItemStack>) {
        this.items = items
    }

    override fun createMenu(
        containerId: Int,
        inventory: Inventory
    ): AbstractContainerMenu = MithrilineFurnaceMenu(containerId, inventory, this, this, ContainerLevelAccess.create(this.level!!, this.blockPos), containerData)

    override fun getContainerSize(): Int = this.items.size

    override val mruStorage: MRUStorageContainer = MRUStorageContainer(10000, MRUTypeRegistry.espe)
    override val balance = MRUBalanceContainer { setChanged() }
    override val deviceType: MRUDevice.DeviceType = MRUDevice.DeviceType.UNCONNECTABLE

    override fun getSlotsForFace(direction: Direction): IntArray = intArrayOf(0, 1)

    override fun canPlaceItemThroughFace(
        slot: Int,
        itemStack: ItemStack,
        direction: Direction?
    ): Boolean = this.canPlaceItem(slot, itemStack)

    override fun canTakeItemThroughFace(
        slot: Int,
        itemStack: ItemStack,
        direction: Direction
    ): Boolean = slot == 0

    override fun canPlaceItem(slot: Int, itemStack: ItemStack): Boolean = if (slot == 1) false else super<SynchronizedContainerBlockEntity>.canPlaceItem(slot, itemStack)

    companion object {
        @JvmStatic
        fun hasValidStructure(level: Level, pos: BlockPos): Boolean =
            MultiblockRegistry.mithrilineFurnace.findPlacementAtCenter(level, pos) != null

        @JvmStatic
        fun onTick(level: Level, pos: BlockPos, be: MithrilineFurnaceEntity) {
            val oldValid = be.structureIsValid
            val newValid = hasValidStructure(level, pos)

            if (oldValid != newValid) {
                be.structureIsValid = newValid
                be.setChanged()
            }

            if (level.isClientSide) {
                val system = ClientParticleSystems.system(level)
                val activeId = "${ECRModIDs.MITHRILINE_FURNACE}/active".ecPrefix
                val activeLeftId = "${ECRModIDs.MITHRILINE_FURNACE}/active_left".ecPrefix

                be.processRotation()
                if (be.snowstormEmitters.isEmpty() && be.structureIsValid) {
                    val active = BedrockParticles[activeId] ?: return
                    val activeLeft = BedrockParticles[activeLeftId] ?: return

                    be.snowstormEmitters += system.spawn(active, transform = be.snowstormTransform)
                    be.snowstormEmitters += system.spawn(activeLeft, transform = be.snowstormTransform)
                } else if (!be.structureIsValid && be.snowstormEmitters.isNotEmpty()) {
                    be.snowstormEmitters.forEach { it.stopLoop() }
                    be.snowstormEmitters.clear()
                }

                return
            }

            if (be.structureIsValid) {
                be.generateESPE(level, pos)
                be.processRecipeIfPresent(level)
            } else be.resetProgress()
        }

        @JvmStatic
        private fun MithrilineFurnaceEntity.generateESPE(level: Level, pos: BlockPos) {
            var downCrystalCount = 0
            var upCrystalCount = 0

            for (x in -2..2) {
                for (z in -2..2) {
                    val xo = pos.x + x
                    val zo = pos.z + z
                    val bpDown = BlockPos(xo, pos.y + 1, zo)
                    val bsDown = level.getBlockState(bpDown)

                    if (bsDown.`is`(BlockRegistry.mithrilineCrystal.get()) && downCrystalCount + 1 <= 12)
                        downCrystalCount++

                    val bpUp = BlockPos(xo, pos.y + 3, zo)
                    val bsUp = level.getBlockState(bpUp)

                    if (bsUp.`is`(BlockRegistry.mithrilineCrystal.get()) && upCrystalCount + 1 <= 5)
                        upCrystalCount++
                }
            }

            val crystalCount = downCrystalCount + upCrystalCount
            if (crystalCount <= 0 || this.mruStorage.isFilled) {
                this.espeGenerationRemainder = 0.0
                return
            }

            this.espeGenerationRemainder += crystalCount * 0.05

            val generateAmount = this.espeGenerationRemainder.toInt()
            if (generateAmount <= 0) return

            val inserted = this.mruStorage.insert(generateAmount)

            if (inserted <= 0 || inserted < generateAmount) this.espeGenerationRemainder = 0.0
            else this.espeGenerationRemainder -= inserted.toDouble()

            this.setChanged()
        }

        @JvmStatic
        private fun MithrilineFurnaceEntity.processRecipeIfPresent(level: Level) {
            val input = this.getItem(0)
            if (input.isEmpty) {
                this.resetProgress()
                return
            }

            val craftingInput = SingleRecipeInput(input)
            val recipe = this.recipe.testAndGet(craftingInput, level)

            if (recipe == null) {
                this.resetProgress()
                return
            }

            val espe = recipe.espe
            val result = recipe.assemble(craftingInput)

            this.slownessGeneration = true
            this.maxCraftProgress = recipe.espe

            if (StackHelper.canCombine(result.copy(), this.getItem(1), input.count, recipe.input.count)) {
                this.processTick(espe)
                if (this.craftProgress >= espe) {
                    this.removeItem(0, recipe.input.count)
                    if (this.getItem(1).isEmpty)
                        this.setItem(1, result.copy())
                    else this.getItem(1).grow(result.count)
                    this.resetProgress()
                }
            }
        }

        private fun MithrilineFurnaceEntity.processTick(mru: Int) {
            val storage = this.mruStorage
            val extractionStep = (1..1000).reversed().firstOrNull { this.canExtract(mru, it) } ?: 0

            if (extractionStep <= 0) return

            val extracted = storage.extract(extractionStep)
            if (extracted <= 0) return

            this.craftProgress += extracted
            this.setChanged()
        }

        @JvmStatic
        private fun MithrilineFurnaceEntity.canExtract(mru: Int, max: Int): Boolean {
            val storage = this.mruStorage
            return storage.canExtract(mru) && mru >= (max + this.craftProgress)
        }

        @JvmStatic
        private fun MithrilineFurnaceEntity.resetProgress() {
            if (this.slownessGeneration) this.slownessGeneration = false
            this.craftProgress = 0
            this.maxCraftProgress = 0
            this.setChanged()
        }

        // Client Only
        @JvmStatic
        private fun MithrilineFurnaceEntity.processRotation() {
            this.coreRotationPrevious = this.coreRotationAngle

            if (this.structureIsValid)
                this.coreRotationAngle += 45f * (1f / 20)
            else if (this.coreRotationAngle % 90 != 0f) {
                this.coreRotationAngle += 45 * (1f / 20) / 2
                if (this.coreRotationAngle % 90 == 0f)
                    this.coreRotationAngle = 90f * floor(this.coreRotationAngle / 90)
            }
        }
    }
}
