package com.algorithmlx.ecr.client.renderer

import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblocks
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.LevelRenderState
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes

object BoundGemLinkRenderer {
    private const val BOUND_GEM_OUTLINE_COLOR = 0xD9FFCA85.toInt()

    fun submit(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        levelRenderState: LevelRenderState
    ) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        val player = minecraft.player ?: return
        val (stack, gem) = boundGemInHands(player.mainHandItem, player.offhandItem) ?: return
        val boundPos = gem.getBoundPos(stack) ?: return
        val boundDimension = gem.getWorld(stack)

        if (boundDimension != null && boundDimension != level.dimension()) return
        if (!level.isLoaded(boundPos)) return

        val blockState = level.getBlockState(boundPos)
        val collisionContext = CollisionContext.of(player)
        val blockShape = (AssembledMultiblocks.formedSelectionShape(level, boundPos)
            ?: blockState.getShape(level, boundPos, collisionContext))
            .takeUnless { it.isEmpty }
            ?: Shapes.block()
        val cameraPos = levelRenderState.cameraRenderState.pos
        val lineWidth = minecraft.gameRenderer.gameRenderState()
            .windowRenderState
            .appropriateLineWidth

        poseStack.pushPose()
        poseStack.translate(
            boundPos.x - cameraPos.x,
            boundPos.y - cameraPos.y,
            boundPos.z - cameraPos.z
        )
        collector.submitShapeOutline(
            poseStack,
            blockShape,
            RenderTypes.lines(),
            BOUND_GEM_OUTLINE_COLOR,
            lineWidth,
            false
        )
        poseStack.popPose()
    }

    private fun boundGemInHands(mainHand: ItemStack, offHand: ItemStack): Pair<ItemStack, BoundGem>? {
        sequenceOf(mainHand, offHand).forEach { stack ->
            val gem = stack.item as? BoundGem ?: return@forEach
            if (gem.getBoundPos(stack) != null) return stack to gem
        }
        return null
    }
}
