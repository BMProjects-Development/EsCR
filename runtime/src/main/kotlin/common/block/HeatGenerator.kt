package com.algorithmlx.ecr.common.block

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.utils.checkAndOpenMenu
import com.algorithmlx.ecr.api.utils.simpleTicker
import com.algorithmlx.ecr.common.block.entity.HeatGeneratorEntity
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.ItemRegistry
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.BlockItemStateProperties
import net.minecraft.world.item.context.BlockPlaceContext
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityTicker
import net.minecraft.world.level.block.entity.BlockEntityType
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.StateDefinition
import net.minecraft.world.level.block.state.properties.BooleanProperty
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.phys.BlockHitResult

class HeatGenerator(properties: Properties): Block(properties), EntityBlock {
    init {
        this.registerDefaultState(this.stateDefinition.any().setValue(IS_UPGRADED, false))
    }

    override fun newBlockEntity(worldPosition: BlockPos, blockState: BlockState): BlockEntity = HeatGeneratorEntity(worldPosition, blockState)

    override fun <T : BlockEntity> getTicker(level: Level, blockState: BlockState, type: BlockEntityType<T>): BlockEntityTicker<T> = simpleTicker<T, HeatGeneratorEntity> { tickLevel, pos, _, blockEntity -> HeatGeneratorEntity.onTick(tickLevel, pos, blockEntity) }

    override fun useWithoutItem(state: BlockState, level: Level, pos: BlockPos, player: Player, hitResult: BlockHitResult): InteractionResult {
        if (!player.isSecondaryUseActive) return checkAndOpenMenu<HeatGeneratorEntity>(player, level, pos)

        if (!level.isClientSide) {
            val blockEntity = level.getBlockEntity(pos) as? HeatGeneratorEntity ?: return InteractionResult.FAIL
            val unit = blockEntity.cycleTemperatureUnit()
            player.sendOverlayMessage(Component.translatable("message.$ModId.heat_generator.temperature_unit", Component.translatable("temperature_unit.$ModId.${unit.serializedName}"), unit.formatCelsius(blockEntity.temperatureCelsius)))
        }
        return InteractionResult.SUCCESS
    }

    override fun useItemOn(
        itemStack: ItemStack,
        state: BlockState,
        level: Level,
        pos: BlockPos,
        player: Player,
        hand: InteractionHand,
        hitResult: BlockHitResult
    ): InteractionResult {
        if (itemStack.`is`(ItemRegistry.instance.heatCore)) {
            val blockEntityO = level.getBlockEntity(pos, BlockEntityTypeRegistry.instance.heatGenerator)
            if (!blockEntityO.isPresent) return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult)
            val be = blockEntityO.get()

            if (be.isUpgraded) return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult)

            be.isUpgraded = true
            itemStack.shrink(1)

            return InteractionResult.SUCCESS
        }

        return super.useItemOn(itemStack, state, level, pos, player, hand, hitResult)
    }

    override fun getStateForPlacement(context: BlockPlaceContext): BlockState {
        val isUpgraded = context.itemInHand[DataComponents.BLOCK_STATE]?.get(IS_UPGRADED) ?: false
        return this.defaultBlockState().setValue(IS_UPGRADED, isUpgraded)
    }

    override fun setPlacedBy(level: Level, pos: BlockPos, state: BlockState, placer: LivingEntity?, stack: ItemStack) {
        super.setPlacedBy(level, pos, state, placer, stack)
        val blockEntity = level.getBlockEntity(pos) as? HeatGeneratorEntity ?: return
        blockEntity.isUpgraded = state.getValue(IS_UPGRADED) || blockEntity.isUpgraded
    }

    override fun createBlockStateDefinition(builder: StateDefinition.Builder<Block, BlockState>) {
        builder.add(IS_UPGRADED)
    }

    override fun getDrops(state: BlockState, params: LootParams.Builder): List<ItemStack> {
        val drops = super.getDrops(state, params)
        drops.firstOrNull { it.`is`(this.asItem()) }?.storeUpgradeState(state.getValue(IS_UPGRADED))
        return drops
    }

    override fun getCloneItemStack(level: LevelReader, pos: BlockPos, state: BlockState, includeData: Boolean): ItemStack = super.getCloneItemStack(level, pos, state, includeData).storeUpgradeState(state.getValue(IS_UPGRADED))

    private fun ItemStack.storeUpgradeState(isUpgraded: Boolean): ItemStack = apply {
        if (isUpgraded) {
            this[DataComponents.BLOCK_STATE] = BlockItemStateProperties.EMPTY.with(IS_UPGRADED, true)
            this[DataComponents.ITEM_NAME] = Component.translatable("block.$ModId.ultra_${ECRModIDs.HEAT_GENERATOR}")
        }
    }

    companion object {
        @JvmField
        val IS_UPGRADED = BooleanProperty.create("upgraded")
    }
}
