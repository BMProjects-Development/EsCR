package com.algorithmlx.ecr.init.events

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.item.SoulStoneLike
import com.algorithmlx.ecr.api.mru.MRUMultiplierWeapon
import com.algorithmlx.ecr.api.recipe.CachedRecipe
import com.algorithmlx.ecr.api.utils.countByIngredient
import com.algorithmlx.ecr.common.components.SoulStoneComponent
import com.algorithmlx.ecr.common.components.updatePlayerMatrix
import com.algorithmlx.ecr.common.data.SoulStoneData
import com.algorithmlx.ecr.common.recipe.StructureRecipe
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.network.BoundGemTargetStatus
import com.algorithmlx.ecr.network.BoundGemTooltipNetwork
import com.algorithmlx.ecr.network.SoulStoneTooltipNetwork
import com.algorithmlx.ecr.registry.DataComponentRegistry
import com.algorithmlx.ecr.registry.ItemRegistry
import net.minecraft.ChatFormatting
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.crafting.SingleRecipeInput
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.Vec3
import kotlin.math.roundToInt

object ECEvents {
    @JvmStatic
    fun itemTooltip(stack: ItemStack, tooltips: MutableList<Component>) {
        when (val item = stack.item) {
            is SoulStoneLike -> {
                val component = stack.getOrDefault(DataComponentRegistry.soulStone.get(), SoulStoneComponent.EMPTY)

                if (component == SoulStoneComponent.EMPTY) return

                tooltips += if (component.ownerName.isNotEmpty())
                    Component.translatable(
                        "tooltip.$ModId.soul_stone.tracking",
                        Component.literal(component.ownerName).withStyle(ChatFormatting.GOLD)
                    ).withStyle(ChatFormatting.DARK_GRAY)
                else Component.translatable("tooltip.$ModId.soul_stone.error").withStyle(ChatFormatting.DARK_RED)

                val mru = SoulStoneTooltipNetwork.tooltipMru(component.owner)
                if (mru != null) {
                    tooltips += Component.translatable(
                        "tooltip.$ModId.soul_stone.detected_ubmru",
                        Component.literal(mru.toString()).withStyle(ChatFormatting.GREEN)
                    ).withStyle(ChatFormatting.DARK_GRAY)
                }
            }

            is BoundGem -> {
                addBoundGemTooltip(stack, item, tooltips)
            }
        }
    }

    @JvmStatic
    fun livingDeath(entity: LivingEntity, source: DamageSource) {
        val player = source.entity as? ServerPlayer ?: return
        val hasOwnedSoulStone = player.inventory.contains { stack ->
            stack.item is SoulStoneLike &&
                    stack.get(DataComponentRegistry.soulStone.get())?.owner == player.uuid
        }
        if (!hasOwnedSoulStone) return

        val weapon = player.getItemInHand(InteractionHand.MAIN_HAND).item
        val multiplier = if (weapon is MRUMultiplierWeapon) weapon.multiplier else 1F
        val addCount = if (entity.type in SoulStoneData.ENTITY_CAPACITY_ADD) {
            SoulStoneData.ENTITY_CAPACITY_ADD.getValue(entity.type).random() * multiplier
        } else {
            val range = if (entity is Enemy) SoulStoneData.defaultEnemyAdd else SoulStoneData.defaultCapacityAdd
            range.random() * multiplier
        }

        player.updatePlayerMatrix { insert(addCount.roundToInt()) }
    }

    private fun addBoundGemTooltip(stack: ItemStack, item: BoundGem, tooltips: MutableList<Component>) {
        val pos = item.getBoundPos(stack) ?: return

        tooltips += Component.translatable("tooltip.$ModId.${ECRModIDs.BOUND_GEM}.linked.pos")
            .append(":")
            .withStyle(ChatFormatting.GOLD)
        tooltips += Component.literal("X").withStyle(ChatFormatting.RED)
            .append(": ").append(Component.literal(pos.x.toString()))
            .append(" ").append(Component.literal("Y").withStyle(ChatFormatting.GREEN))
            .append(": ").append(Component.literal(pos.y.toString()))
            .append(" ").append(Component.literal("Z").withStyle(ChatFormatting.BLUE))
            .append(": ").append(Component.literal(pos.z.toString()))

        if (BoundGemTooltipNetwork.tooltipStatus(stack, item) == BoundGemTargetStatus.NOT_MRU) {
            tooltips += Component.translatable("tooltip.$ModId.${ECRModIDs.BOUND_GEM}.linked.not_mru")
                .withStyle(ChatFormatting.GOLD)
        }

        if (BoundGemTooltipNetwork.tooltipStatus(stack, item) == BoundGemTargetStatus.MRU_CONNECTABLE_NOT_EXPORTER) {
            tooltips += Component.translatable("tooltip.$ModId.${ECRModIDs.BOUND_GEM}.linked.mru_connectable_not_exporter")
                .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
        }

        if (item.isOutsideBoundRadius(stack)) {
            tooltips += Component.translatable("tooltip.$ModId.${ECRModIDs.BOUND_GEM}.linked.outside_radius")
                .withStyle(ChatFormatting.RED)
        }

        if (!item.dimensionalBounds) {
            tooltips += Component.translatable("tooltip.$ModId.${ECRModIDs.BOUND_GEM}.dimension.disallowed")
                .withStyle(ChatFormatting.RED)
        }
    }

    @JvmStatic
    fun itemEntityTickCraft(
        stack: ItemStack, cached: CachedRecipe<SingleRecipeInput, StructureRecipe>, pos: Vec3,
        level: Level, timer: IntArray
    ) {
        if (stack.`is`(ItemRegistry.hammer.get())) return

        val center = BlockPos.containing(pos).below()

        val craftingInput = SingleRecipeInput(stack)
        val recipe = cached.testAndGet(craftingInput, level) ?: return

        val state = level.getBlockState(center)
        val isAtCenter = recipe.structureCenter?.let { state.`is`(it) } ?: true

        val placement = if (recipe.structureCenter == null)
            recipe.multiblock.findPlacement(level, center)
        else recipe.multiblock.findPlacementAtCenter(level, center)

        if (!isAtCenter || placement == null) {
            timer[0] = 0
            return
        }

        if (!level.getBlockState(center.above()).`is`(Blocks.AIR)) return

        val place = recipe.blockForPlace
        val result = recipe.assemble(craftingInput)

        timer[0] = timer[0] + 1
        if (timer[0] < recipe.time) return

        timer[0] = 0

        stack.shrink(countByIngredient(recipe.ingredient))

        if (recipe.consumeStructure) recipe.multiblock.replaceInWorld(level, placement) { Blocks.AIR.defaultBlockState() }

        if (!(recipe.chance.isEmpty() || level.random.nextInt(recipe.chance.max) >= recipe.chance.min)) return

        if (place != null) {
            level.setBlock(
                center.above(),
                place.defaultBlockState(),
                Block.UPDATE_NEIGHBORS or Block.UPDATE_CLIENTS or Block.UPDATE_SUPPRESS_DROPS
            )
        } else {
            val item = ItemEntity(level, pos.x, pos.y, pos.z, result).apply { this.setNoPickUpDelay() }
            level.addFreshEntity(item)
        }
    }
}
