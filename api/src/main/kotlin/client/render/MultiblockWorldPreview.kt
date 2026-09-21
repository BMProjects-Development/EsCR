package com.algorithmlx.ecr.api.client.render

import com.algorithmlx.ecr.api.multiblock.Multiblock
import com.algorithmlx.ecr.api.multiblock.MultiblockMatcher
import com.algorithmlx.ecr.api.multiblock.MultiblockPattern
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.client.renderer.state.level.LevelRenderState
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.CollisionContext
import net.minecraft.world.phys.shapes.Shapes

object MultiblockWorldPreview {
    private val renderer by lazy(::MultiblockPreviewRenderer)

    @Volatile
    private var active: ActivePreview? = null

    @JvmStatic
    fun place(
        multiblock: Multiblock,
        center: BlockPos,
        direction: Direction = Direction.NORTH
    ): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val pattern = multiblock.variants.firstOrNull() ?: return false
        active =
            ActivePreview(
                pattern,
                center.immutable(),
                direction.takeIf { it.axis.isHorizontal } ?: Direction.NORTH,
                level.dimension()
            )
        return true
    }

    @JvmStatic
    fun clear() {
        active = null
    }

    @JvmStatic
    fun isActive(): Boolean = active != null

    @JvmStatic
    fun center(): BlockPos? = active?.center

    @JvmStatic
    fun tick(level: Level) {
        val preview = active ?: return
        if (preview.dimension != level.dimension()) {
            clear()
            return
        }
        val expected = preview.expectedBlocks
        if (expected.any { !level.isLoaded(it.worldPosition) }) return
        if (expected.all { it.matcher.matches(level.getBlockState(it.worldPosition)) }) clear()
    }

    @JvmStatic
    fun submit(
        poseStack: PoseStack,
        collector: SubmitNodeCollector,
        levelRenderState: LevelRenderState
    ) {
        val preview = active ?: return
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        if (preview.dimension != level.dimension()) return

        val missing =
            preview.expectedBlocks.filter { expected ->
                level.isLoaded(expected.worldPosition) &&
                    !expected.matcher.matches(level.getBlockState(expected.worldPosition))
            }
        if (missing.isEmpty()) return

        val camera = levelRenderState.cameraRenderState.pos
        poseStack.pushPose()
        poseStack.translate(
            preview.center.x - camera.x,
            preview.center.y - camera.y,
            preview.center.z - camera.z
        )
        renderer.submit(
            missing
                .asSequence()
                .filterNot { it.previewState.isAir }
                .map { it.relativePosition to it.previewState }
                .toList(),
            poseStack,
            collector,
            MODEL_OUTLINE_COLOR
        )

        val lineWidth = minecraft.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth
        missing.forEach { expected ->
            val currentState = level.getBlockState(expected.worldPosition)
            val shape =
                expected.previewState
                    .getShape(level, expected.worldPosition, CollisionContext.empty())
                    .takeUnless { it.isEmpty }
                    ?: Shapes.block()
            poseStack.pushPose()
            poseStack.translate(
                expected.relativePosition.x.toDouble(),
                expected.relativePosition.y.toDouble(),
                expected.relativePosition.z.toDouble()
            )
            collector.submitShapeOutline(
                poseStack,
                shape,
                RenderTypes.lines(),
                if (currentState.isAir) MISSING_OUTLINE_COLOR else WRONG_OUTLINE_COLOR,
                lineWidth,
                false
            )
            poseStack.popPose()
        }
        poseStack.popPose()
    }

    private data class ActivePreview(
        val pattern: MultiblockPattern,
        val center: BlockPos,
        val direction: Direction,
        val dimension: ResourceKey<Level>
    ) {
        val expectedBlocks: List<ExpectedBlock> = buildList {
            pattern.blocks.forEachIndexed { index, matcher ->
                if (!matcher.required) return@forEachIndexed
                val patternPosition = pattern.positionOf(index)
                val relative =
                    rotate(
                        patternPosition.x - pattern.center.x,
                        patternPosition.y - pattern.center.y,
                        patternPosition.z - pattern.center.z,
                        direction
                    )
                add(
                    ExpectedBlock(
                        center.offset(relative),
                        relative,
                        matcher.default(),
                        matcher
                    )
                )
            }
        }
    }

    private data class ExpectedBlock(
        val worldPosition: BlockPos,
        val relativePosition: BlockPos,
        val previewState: BlockState,
        val matcher: MultiblockMatcher
    )

    private fun rotate(
        x: Int,
        y: Int,
        z: Int,
        direction: Direction
    ): BlockPos = when (direction) {
        Direction.NORTH -> BlockPos(x, y, -z)
        Direction.SOUTH -> BlockPos(-x, y, z)
        Direction.WEST -> BlockPos(-z, y, -x)
        Direction.EAST -> BlockPos(z, y, x)
        else -> BlockPos(x, y, -z)
    }

    private const val MODEL_OUTLINE_COLOR = 0xCC6FE7FF.toInt()
    private const val MISSING_OUTLINE_COLOR = 0xE06FE7FF.toInt()
    private const val WRONG_OUTLINE_COLOR = 0xE0FF6B5F.toInt()
}
