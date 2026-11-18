package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.common.block.entity.NaturalFurnaceEntity
import com.algorithmlx.ecr.common.machine.MachineBlock
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.BlockItemStateProperties
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.storage.loot.LootParams

class NaturalFurnace(properties: Properties): MachineBlock(properties, ::NaturalFurnaceEntity) {
    init { registerDefaultState(stateDefinition.any().setValue(IS_UPGRADED, false)) }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) { builder.add(IS_UPGRADED) }
    override fun getStateForPlacement(context: BlockPlaceContext): BlockState = defaultBlockState().setValue(IS_UPGRADED, context.itemInHand[DataComponents.BLOCK_STATE]?.get(IS_UPGRADED) ?: false)
    override fun getDrops(state: BlockState, params: LootParams.Builder): List<ItemStack> = listOf(stack(state.getValue(IS_UPGRADED)))
    override fun getCloneItemStack(level: LevelReader, pos: BlockPos, state: BlockState, includeData: Boolean): ItemStack = stack(state.getValue(IS_UPGRADED))

    fun stack(upgraded: Boolean): ItemStack = ItemStack(this).apply {
        if (upgraded) {
            this[DataComponents.BLOCK_STATE] = BlockItemStateProperties.EMPTY.with(IS_UPGRADED, true)
            this[DataComponents.ITEM_NAME] = Component.translatableWithFallback("block.$ModId.ultra_natural_furnace", "Ultra Natural Furnace")
        }
    }

    companion object {
        @JvmField val IS_UPGRADED = BooleanProperty.create("upgraded")
    }
}
