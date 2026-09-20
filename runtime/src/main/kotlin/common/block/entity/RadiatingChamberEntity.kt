package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.api.block.entity.SynchronizedContainerBlockEntity
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.loadMRUData
import com.algorithmlx.ecr.api.mru.processReceive
import com.algorithmlx.ecr.api.mru.saveMRUData
import com.algorithmlx.ecr.api.mru.storage.MRUStorageContainer
import com.algorithmlx.ecr.api.recipe.CachedRecipe
import com.algorithmlx.ecr.api.utils.StackHelper
import com.algorithmlx.ecr.api.utils.count
import com.algorithmlx.ecr.common.menu.RadiatingChamberMenu
import com.algorithmlx.ecr.common.recipe.RadiatingChamberRecipe
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.BlockRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import com.algorithmlx.ecr.registry.RecipeTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.ContainerHelper
import net.minecraft.world.WorldlyContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput

class RadiatingChamberEntity(
    worldPosition: BlockPos,
    blockState: BlockState
): SynchronizedContainerBlockEntity(BlockEntityTypeRegistry.radiatingChamber.get(), worldPosition, blockState), MRUDevice, WorldlyContainer {
    @all:JvmName("items")
    private var items = NonNullList.withSize(4, ItemStack.EMPTY)

    val recipe = CachedRecipe(RecipeTypeRegistry.radiatingChamber.get())
    var craftProgress = 0
    var maxCraftProgress = 0
    private var activeRecipe: Identifier? = null

    override val mruStorage = MRUStorageContainer(5000, MRUTypeRegistry.radiationUnit) { setChanged() }
    override val balance = MRUBalanceContainer { setChanged() }
    override val deviceType: MRUDevice.DeviceType = MRUDevice.DeviceType.RECEIVER
    override val locator = MRUDevice.LocatorData(this, BOUND_GEM_SLOT)

    override fun saveAdditional(output: ValueOutput) {
        ContainerHelper.saveAllItems(output, this.items)
        output.putInt("progress", this.craftProgress)
        output.putInt("max_progress", this.maxCraftProgress)
        this.activeRecipe?.let { output.putString("recipe", it.toString()) }
        saveMRUData(output)
        super.saveAdditional(output)
    }

    override fun loadAdditional(input: ValueInput) {
        this.items = NonNullList.withSize(4, ItemStack.EMPTY)
        ContainerHelper.loadAllItems(input, this.items)
        this.maxCraftProgress = input.getIntOr("max_progress", 0).coerceAtLeast(0)
        this.craftProgress = input.getIntOr("progress", 0).coerceIn(0, this.maxCraftProgress)
        this.activeRecipe = Identifier.tryParse(input.getStringOr("recipe", ""))
        this.recipe.recipeHolder = null
        loadMRUData(input)
        super.loadAdditional(input)
    }

    override fun getDefaultName(): Component = BlockRegistry.radiatingChamber.get().name

    override fun getItems(): NonNullList<ItemStack> = this.items

    override fun setItems(items: NonNullList<ItemStack>) {
        this.items = items
    }

    override fun getContainerSize(): Int = this.items.size

    override fun createMenu(containerId: Int, inventory: Inventory): AbstractContainerMenu =
        RadiatingChamberMenu(containerId, inventory, this, this, ContainerLevelAccess.create(this.level!!, this.blockPos))

    override fun getSlotsForFace(direction: Direction): IntArray = when (direction) {
        Direction.UP -> intArrayOf(INPUT_SLOT)
        Direction.DOWN -> intArrayOf(OUTPUT_SLOT)
        else -> intArrayOf(SECONDARY_SLOT)
    }

    override fun canPlaceItemThroughFace(slot: Int, itemStack: ItemStack, direction: Direction?): Boolean = canPlaceItem(slot, itemStack)

    override fun canTakeItemThroughFace(slot: Int, itemStack: ItemStack, direction: Direction): Boolean = slot == OUTPUT_SLOT

    override fun canPlaceItem(slot: Int, itemStack: ItemStack): Boolean = when (slot) {
        BOUND_GEM_SLOT -> itemStack.item is BoundGem
        INPUT_SLOT, SECONDARY_SLOT -> true
        else -> false
    }

    companion object {
        const val BOUND_GEM_SLOT = 0
        const val INPUT_SLOT = 1
        const val SECONDARY_SLOT = 2
        const val OUTPUT_SLOT = 3
        const val DATA_COUNT = 4

        @JvmStatic
        fun onTick(level: Level, pos: BlockPos, be: RadiatingChamberEntity) {
            if (level.isClientSide) return
            be.processReceive(level)
            if (level.hasNeighborSignal(pos)) return
            be.processRecipeIfPresent(level)
        }

        @JvmStatic
        private fun RadiatingChamberEntity.processRecipeIfPresent(level: Level) {
            val cached = this.recipe.recipeHolder
            if (level is ServerLevel && cached != null && level.recipeAccess().byKey(cached.id).orElse(null) !== cached) {
                this.recipe.recipeHolder = null
                this.resetProgress()
            }

            val input = RadiatingChamberRecipe.Input(this.getItem(INPUT_SLOT), this.getItem(SECONDARY_SLOT), this.balance)
            val recipe = this.recipe.testAndGet(input, level)
            if (recipe == null) {
                this.resetProgress()
                return
            }

            val result = recipe.assemble(input)
            val output = this.getItem(OUTPUT_SLOT)
            if (result.isEmpty || !StackHelper.canCombineStacks(result, output) || result.count > this.maxStackSize.coerceAtMost(result.maxStackSize) - output.count) {
                this.resetProgress()
                return
            }

            val recipeId = this.recipe.identifier()
            if (this.activeRecipe != recipeId || this.maxCraftProgress != recipe.time) {
                this.craftProgress = 0
                this.maxCraftProgress = recipe.time
                this.activeRecipe = recipeId
                this.setChanged()
            }

            if (!this.mruStorage.canExtract(recipe.mruPerTick)) return
            this.mruStorage.extract(recipe.mruPerTick)
            this.craftProgress++

            if (this.craftProgress >= this.maxCraftProgress) {
                this.removeItem(INPUT_SLOT, recipe.input.count)
                if (recipe.secondary.isPresent) this.removeItem(SECONDARY_SLOT, recipe.secondary.get().count)
                if (this.getItem(OUTPUT_SLOT).isEmpty)
                    this.setItem(OUTPUT_SLOT, result)
                else this.getItem(OUTPUT_SLOT).grow(result.count)
                this.resetProgress()
            } else this.setChanged()
        }

        @JvmStatic
        private fun RadiatingChamberEntity.resetProgress() {
            if (this.craftProgress == 0 && this.maxCraftProgress == 0 && this.activeRecipe == null) return
            this.craftProgress = 0
            this.maxCraftProgress = 0
            this.activeRecipe = null
            this.setChanged()
        }
    }
}
