package com.algorithmlx.ecr.common.inventory

import com.algorithmlx.ecr.common.api.BoundGemHelper
import com.algorithmlx.ecr.init.config.ECConfig
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.network.chat.Component
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.context.UseOnContext
import net.minecraft.world.level.Level

class ControlRodItem(properties: Properties) : Item(properties) {
    override fun useOn(context: UseOnContext): InteractionResult {
        val player = context.player ?: return InteractionResult.PASS
        return interact(player, context.level, context.hand, context.clickedPos, context.clickedFace)
    }

    fun interact(
        player: Player,
        level: Level,
        hand: InteractionHand,
        pos: BlockPos,
        face: Direction
    ): InteractionResult {
        if (level.isClientSide) return InteractionResult.SUCCESS
        if (!player.mayBuild() || !level.isLoaded(pos)) return InteractionResult.FAIL

        val stack = player.getItemInHand(hand)
        val mirror = level.getBlockEntity(pos) as? MagicalMirrorEntity
        if (player.isShiftKeyDown) {
            mirror?.clearLink()
            BoundGemHelper.setBoundPos(stack, null)
            player.sendOverlayMessage(Component.translatable(if (mirror == null) "message.escr.magical_mirror.cleared" else "message.escr.magical_mirror.unlinked"))
            return InteractionResult.SUCCESS
        }

        if (mirror != null) {
            BoundGemHelper.setBoundPos(stack, pos.immutable())
            BoundGemHelper.setLevelKey(stack, level.dimension())
            player.sendOverlayMessage(Component.translatable("message.escr.magical_mirror.selected"))
            return InteractionResult.SUCCESS
        }

        val selectedPos = BoundGemHelper.getBoundPos(stack)
        if (selectedPos == null) {
            player.sendOverlayMessage(Component.translatable("message.escr.magical_mirror.select_first"))
            return InteractionResult.FAIL
        }
        if (BoundGemHelper.getLevelKey(stack) != level.dimension()) {
            player.sendOverlayMessage(Component.translatable("message.escr.magical_mirror.dimension"))
            return InteractionResult.FAIL
        }
        if (!level.isLoaded(selectedPos)) {
            player.sendOverlayMessage(Component.translatable("message.escr.magical_mirror.unloaded"))
            return InteractionResult.FAIL
        }

        val selected = level.getBlockEntity(selectedPos) as? MagicalMirrorEntity
        if (selected == null) {
            BoundGemHelper.setBoundPos(stack, null)
            player.sendOverlayMessage(Component.translatable("message.escr.magical_mirror.missing"))
            return InteractionResult.FAIL
        }
        if (!selected.link(pos, face)) {
            val range = ECConfig.current.magicalMirror.range
            player.sendOverlayMessage(Component.translatable("message.escr.magical_mirror.range", range))
            return InteractionResult.FAIL
        }

        BoundGemHelper.setBoundPos(stack, null)
        player.sendOverlayMessage(Component.translatable(
            "message.escr.magical_mirror.linked", pos.x, pos.y, pos.z,
            Component.translatable("message.escr.magical_mirror.face.${face.serializedName}")
        ))
        return InteractionResult.SUCCESS
    }

    override fun use(level: Level, player: Player, hand: InteractionHand): InteractionResult {
        if (!player.isShiftKeyDown) return InteractionResult.PASS
        if (!level.isClientSide) {
            BoundGemHelper.setBoundPos(player.getItemInHand(hand), null)
            player.sendOverlayMessage(Component.translatable("message.escr.magical_mirror.cleared"))
        }
        return InteractionResult.SUCCESS
    }
}
