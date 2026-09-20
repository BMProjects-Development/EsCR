package com.algorithmlx.ecr.common.item

import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDefinition
import com.algorithmlx.ecr.api.multiblock.assembled.AssemblyResult
import com.algorithmlx.ecr.api.multiblock.MultiblockDefinitions
import com.algorithmlx.ecr.api.recipe.CachedRecipe
import com.algorithmlx.ecr.common.assembled.AssembledMultiblockRuntime
import com.algorithmlx.ecr.common.recipe.StructureRecipe
import com.algorithmlx.ecr.registry.RecipeTypeRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks

class Hammer(
    properties: Properties,
) : Item(properties) {
    private lateinit var cachedRecipe: CachedRecipe<SingleRecipeInput, StructureRecipe>

    override fun useOn(context: UseOnContext): InteractionResult {
        val pos = context.clickedPos
        val level = context.level
        val facing = context.horizontalDirection
        val definition =
            findMatchingDefinition(level, pos, facing)
                ?: return super.useOn(context)

        if (level.isClientSide) return InteractionResult.SUCCESS

        return when (AssembledMultiblockRuntime.assemble(level, definition, pos, facing)) {
            is AssemblyResult.Success -> {
                InteractionResult.SUCCESS
            }

            is AssemblyResult.Failure -> {
                if (!::cachedRecipe.isInitialized) {
                    this.cachedRecipe = CachedRecipe(RecipeTypeRegistry.structure.get())
                }

                val craftingInput = SingleRecipeInput(context.itemInHand)
                val recipe = cachedRecipe.testAndGet(craftingInput, level) ?: return InteractionResult.FAIL

                val state = context.level.getBlockState(context.clickedPos)
                val isAtCenter = recipe.structureCenter?.let { state.`is`(it) } ?: true

                val placement =
                    if (recipe.structureCenter == null) {
                        recipe.multiblock.findPlacement(level, pos)
                    } else {
                        recipe.multiblock.findPlacementAtCenter(level, pos)
                    }

                if (!isAtCenter || placement == null) return InteractionResult.FAIL

                if (!level.getBlockState(pos.above()).`is`(Blocks.AIR)) return InteractionResult.FAIL

                val place = recipe.blockForPlace
                val result = recipe.assemble(craftingInput)

                if (recipe.consumeStructure) {
                    recipe.multiblock.replaceInWorld(level, placement) { Blocks.AIR.defaultBlockState() }
                }

                if (!(recipe.chance.isEmpty() || level.random.nextInt(recipe.chance.max) >= recipe.chance.min)) {
                    return InteractionResult.FAIL
                }

                if (place != null) {
                    level.setBlock(
                        pos.above(),
                        place.defaultBlockState(),
                        Block.UPDATE_NEIGHBORS or Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS,
                    )
                } else {
                    val item =
                        ItemEntity(
                            level,
                            pos.x + 0.5,
                            pos.y + 1.0,
                            pos.z + 0.5,
                            result,
                        ).apply { this.setNoPickUpDelay() }
                    level.addFreshEntity(item)
                }

                InteractionResult.SUCCESS
            }
        }
    }

    private fun findMatchingDefinition(
        level: Level,
        selectedPos: BlockPos,
        facing: Direction,
    ): AssembledMultiblockDefinition? =
        MultiblockDefinitions.allAssembled().values.firstOrNull { definition ->
            definition.controllerCandidates(selectedPos, facing).any { controllerPos ->
                definition.matches(level, controllerPos, facing)
            }
        }
}
