package com.algorithmlx.ecr.common.block.entity

import com.algorithmlx.ecr.api.block.entity.SynchronizedContainerBlockEntity
import com.algorithmlx.ecr.api.mru.MRUDevice
import com.algorithmlx.ecr.api.mru.balance.MRUBalanceContainer
import com.algorithmlx.ecr.api.mru.loadMRUData
import com.algorithmlx.ecr.api.mru.processReceive
import com.algorithmlx.ecr.api.mru.saveMRUData
import com.algorithmlx.ecr.api.mru.storage.IOMRUStorage
import com.algorithmlx.ecr.api.mru.storage.MRUStorageContainer
import com.algorithmlx.ecr.api.recipe.CachedRecipe
import com.algorithmlx.ecr.api.utils.StackHelper
import com.algorithmlx.ecr.api.utils.count
import com.algorithmlx.ecr.common.menu.MagicTableMenu
import com.algorithmlx.ecr.common.recipe.MagicTableRecipe
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.MRUTypeRegistry
import com.algorithmlx.ecr.registry.RecipeTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.NonNullList
import net.minecraft.network.chat.Component
import net.minecraft.world.ContainerHelper
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.ContainerData
import net.minecraft.world.inventory.ContainerLevelAccess
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.CraftingInput
import net.minecraft.world.item.crafting.Ingredient
import net.minecraft.world.item.crafting.ShapedRecipePattern
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.storage.ValueInput
import net.minecraft.world.level.storage.ValueOutput
import kotlin.jvm.optionals.getOrNull

class MagicTableBlockEntity(
    worldPosition: BlockPos,
    blockState: BlockState,
) : SynchronizedContainerBlockEntity(BlockEntityTypeRegistry.magicTable.get(), worldPosition, blockState),
    MRUDevice {
    private var items: NonNullList<ItemStack> = NonNullList.withSize(8, ItemStack.EMPTY)

    private val containerData: ContainerData =
        object : ContainerData {
            override fun get(index: Int): Int =
                when (index) {
                    0 -> this@MagicTableBlockEntity.progress
                    1 -> this@MagicTableBlockEntity.maxProgress
                    else -> 0
                }

            override fun set(
                index: Int,
                value: Int,
            ) {
                when (index) {
                    0 -> this@MagicTableBlockEntity.progress = value
                    1 -> this@MagicTableBlockEntity.maxProgress = value
                }
            }

            override fun getCount(): Int = 2
        }

    private val recipe = CachedRecipe(RecipeTypeRegistry.magicTable.get())

    var progress = 0
    var maxProgress = 0

    override fun getDefaultName(): Component = Component.empty()

    override fun getItems(): NonNullList<ItemStack> = this.items

    override fun setItems(items: NonNullList<ItemStack>) {
        this.items = items
    }

    override fun createMenu(
        containerId: Int,
        inventory: Inventory,
    ): AbstractContainerMenu =
        MagicTableMenu(
            containerId,
            inventory,
            this,
            this,
            ContainerLevelAccess.create(this.level!!, this.blockPos),
            containerData,
        )

    override fun saveAdditional(output: ValueOutput) {
        ContainerHelper.saveAllItems(output, this.items)
        output.putInt("progress", this.progress)
        output.putInt("max_progress", this.maxProgress)
        this.saveMRUData(output)
        super.saveAdditional(output)
    }

    override fun loadAdditional(input: ValueInput) {
        ContainerHelper.loadAllItems(input, this.items)
        this.progress = input.getIntOr("progress", 0)
        this.maxProgress = input.getIntOr("max_progress", 0)
        this.loadMRUData(input)
        super.loadAdditional(input)
    }

    override fun getContainerSize(): Int = this.items.size

    override fun canPlaceItem(
        slot: Int,
        itemStack: ItemStack,
    ): Boolean = if (slot == 5) false else super.canPlaceItem(slot, itemStack)

    override val mruStorage: IOMRUStorage = MRUStorageContainer(5000, MRUTypeRegistry.radiationUnit) { setChanged() }
    override val balance = MRUBalanceContainer { setChanged() }
    override val deviceType: MRUDevice.DeviceType = MRUDevice.DeviceType.RECEIVER

    override val locator: MRUDevice.LocatorData = MRUDevice.LocatorData(this, 6)

    companion object {
        @JvmStatic
        fun onTick(
            level: Level,
            be: MagicTableBlockEntity,
        ) {
            if (level.isClientSide) return
            be.processReceive(level)
            be.processRecipeIfPresent(level)
        }

        private fun MagicTableBlockEntity.processRecipeIfPresent(level: Level) {
            if ((0..<5).all { this.getItem(it).isEmpty }) {
                this.resetProgress()
                return
            }

            val input = MagicTableRecipe.Input((0..<5).map { this.getItem(it) })

            val recipe = this.recipe.testAndGet(input, level)
            if (recipe == null) {
                this.resetProgress()
                return
            }

            val time = recipe.time
            val mru = recipe.mruPerTick
            val result = recipe.result

            if (!StackHelper.canCombineStacks(result.create(), this.getItem(5))) return

            this.maxProgress = time

            this.processTick(time, mru)

            if (time > this.progress) return

            val inputs = recipe.input.getOrNull()

            if (inputs != null) {
                val positioned = input.craftingInput()
                val craftingInput = positioned.input()
                val ingredients = inputs.ingredients()
                val mirrored = shouldConsumeMirrored(inputs, craftingInput)

                (0..<inputs.height()).forEach { y ->
                    (0..<inputs.width()).forEach { x ->
                        val ingredientX = if (mirrored) inputs.width() - x - 1 else x
                        val ingredient = ingredients[ingredientX + y * inputs.width()].getOrNull() ?: return@forEach
                        val slot = x + positioned.left() + (y + positioned.top()) * 2
                        this.removeItem(slot, ingredient.count)
                    }
                }
            }

            val catalyst = recipe.catalyst.getOrNull()
            catalyst?.let { this.removeItem(4, it.count) }

            if (this.getItem(5).isEmpty) {
                this.setItem(5, result.create())
            } else {
                this.getItem(5).grow(result.count())
            }

            this.resetProgress()
        }

        private fun MagicTableBlockEntity.processTick(
            time: Int,
            mru: Int,
        ) {
            val storage = this.mruStorage
            if (this.progress >= time || !storage.canExtract(mru)) return

            storage.extract(mru)
            this.progress++
            this.setChanged()
        }

        private fun MagicTableBlockEntity.resetProgress() {
            this.progress = 0
            this.maxProgress = 0
            this.setChanged()
        }

        private fun shouldConsumeMirrored(
            pattern: ShapedRecipePattern,
            input: CraftingInput,
        ): Boolean = !matchesPattern(pattern, input, mirrored = false) && matchesPattern(pattern, input, mirrored = true)

        private fun matchesPattern(
            pattern: ShapedRecipePattern,
            input: CraftingInput,
            mirrored: Boolean,
        ): Boolean {
            val ingredients = pattern.ingredients()

            (0..<pattern.height()).forEach { y ->
                (0..<pattern.width()).forEach { x ->
                    val ingredientX = if (mirrored) pattern.width() - x - 1 else x
                    val ingredient = ingredients[ingredientX + y * pattern.width()]
                    if (!Ingredient.testOptionalIngredient(ingredient, input.getItem(x, y))) return false
                }
            }

            return true
        }
    }
}
