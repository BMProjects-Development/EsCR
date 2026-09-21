package com.algorithmlx.ecr.api.event.entity.player

import com.algorithmlx.ecr.api.event.engine.ICancellableEvent
import com.algorithmlx.ecr.api.event.entity.PlayerEvent
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.TriState
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3

abstract class PlayerInteractEvent(
    final override val entity: Player,
    val hand: InteractionHand,
    val pos: BlockPos,
    val face: Direction?
): PlayerEvent(entity) {
    val itemStack: ItemStack get() = entity.getItemInHand(hand)
    val level get() = entity.level()

    class EntityInteract(
        entity: Player,
        hand: InteractionHand,
        val target: Entity,
        val location: Vec3
    ): PlayerInteractEvent(entity, hand, target.blockPosition(), null), ICancellableEvent {
        var cancellationResult: InteractionResult = InteractionResult.PASS
    }

    class RightClickBlock(
        entity: Player,
        hand: InteractionHand,
        val hitVec: BlockHitResult
    ): PlayerInteractEvent(entity, hand, hitVec.blockPos, hitVec.direction), ICancellableEvent {
        var cancellationResult: InteractionResult = InteractionResult.PASS
        var useBlock = TriState.DEFAULT
        var useItem = TriState.DEFAULT

        override var isCanceled: Boolean
            get() = super.isCanceled
            set(value) {
                super.isCanceled = value
                if (value) {
                    useBlock = TriState.FALSE
                    useItem = TriState.FALSE
                }
            }
    }

    class RightClickItem(
        entity: Player,
        hand: InteractionHand
    ): PlayerInteractEvent(entity, hand, entity.blockPosition(), null), ICancellableEvent {
        var cancellationResult: InteractionResult = InteractionResult.PASS
    }

    class RightClickEmpty(entity: Player, hand: InteractionHand): PlayerInteractEvent(entity, hand, entity.blockPosition(), null)

    class LeftClickBlock(
        entity: Player,
        pos: BlockPos,
        face: Direction?,
        val action: Action
    ): PlayerInteractEvent(entity, InteractionHand.MAIN_HAND, pos, face), ICancellableEvent {
        var useBlock = TriState.DEFAULT
        var useItem = TriState.DEFAULT

        override var isCanceled: Boolean
            get() = super.isCanceled
            set(value) {
                super.isCanceled = value
                if (value) {
                    useBlock = TriState.FALSE
                    useItem = TriState.FALSE
                }
            }

        enum class Action {
            START, STOP, ABORT, CLIENT_HOLD
        }
    }

    class LeftClickEmpty(entity: Player): PlayerInteractEvent(entity, InteractionHand.MAIN_HAND, entity.blockPosition(), null)
}
