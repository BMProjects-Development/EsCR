package com.algorithmlx.ecr.api.client.render

import com.algorithmlx.ecr.api.block.Multipart
import com.algorithmlx.ecr.api.geo.client.BedrockGeoRenderData
import com.algorithmlx.ecr.api.geo.client.BedrockGeoRenderEngine
import com.algorithmlx.ecr.api.molang.runtime.MolangContext
import com.algorithmlx.ecr.api.multiblock.Multiblock
import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblockDefinition
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.block.BlockModelRenderState
import net.minecraft.client.renderer.block.BlockModelResolver
import net.minecraft.client.renderer.block.model.BlockDisplayContext
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.util.LightCoordsUtil
import net.minecraft.util.Mth
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.min

class MultiblockPreviewRenderer(
    private val modelResolver: BlockModelResolver = BlockModelResolver(Minecraft.getInstance().modelManager)
) {
    private val displayContext = BlockDisplayContext.create()
    private val modelState = BlockModelRenderState()

    fun submit(
        multiblock: Multiblock,
        poseStack: PoseStack,
        submitter: SubmitNodeCollector,
        bounds: MultiblockPreviewBounds,
        transform: MultiblockPreviewTransform = MultiblockPreviewTransform()
    ) {
        poseStack.pushPose()
        applyGuiTransform(multiblock, poseStack, bounds, transform)
        submitBlocks(multiblock, poseStack, submitter, transform)
        poseStack.popPose()
    }

    fun submit(
        blocks: List<Pair<BlockPos, BlockState>>,
        poseStack: PoseStack,
        submitter: SubmitNodeCollector,
        outlineColor: Int = -1,
    ) {
        submitBlocks(
            blocks.flatMap { (position, state) -> previewParts(position, state) },
            poseStack,
            submitter,
            outlineColor,
        )
    }

    fun submit(
        definition: AssembledMultiblockDefinition,
        assembled: Boolean,
        poseStack: PoseStack,
        submitter: SubmitNodeCollector,
        bounds: MultiblockPreviewBounds,
        transform: MultiblockPreviewTransform = MultiblockPreviewTransform()
    ) {
        if (assembled) {
            submitFormed(definition, poseStack, submitter, bounds, transform)
            return
        }

        val blocks = previewBlocks(definition, transform)
        val pivot = Vector3f(
            (definition.minOffset.x + definition.maxOffset.x + 1) / 2f,
            definition.minOffset.y.toFloat(),
            (definition.minOffset.z + definition.maxOffset.z + 1) / 2f
        )
        poseStack.pushPose()
        applyGuiTransform(blocks, pivot, poseStack, bounds, transform)
        submitBlocks(blocks, poseStack, submitter)
        poseStack.popPose()
    }

    fun submit(
        multiblock: Multiblock,
        poseStack: PoseStack,
        submitter: SubmitNodeCollector,
        transform: MultiblockPreviewTransform = MultiblockPreviewTransform()
    ) {
        poseStack.pushPose()
        applyWorldTransform(multiblock, poseStack, transform)
        submitBlocks(multiblock, poseStack, submitter, transform)
        poseStack.popPose()
    }

    private fun applyGuiTransform(
        multiblock: Multiblock,
        poseStack: PoseStack,
        bounds: MultiblockPreviewBounds,
        transform: MultiblockPreviewTransform
    ) {
        val blocks = previewBlocks(multiblock, transform)
        val pivot = Vector3f(multiblock.xSize / 2f, 0f, multiblock.zSize / 2f)
        applyGuiTransform(blocks, pivot, poseStack, bounds, transform)
    }

    private fun applyGuiTransform(
        blocks: List<PreviewBlock>,
        pivot: Vector3f,
        poseStack: PoseStack,
        bounds: MultiblockPreviewBounds,
        transform: MultiblockPreviewTransform
    ) {
        val projected = projectedBounds(blocks, pivot, transform)
        val availableWidth = bounds.width.coerceAtLeast(1f)
        val availableHeight = bounds.height.coerceAtLeast(1f)
        val fittedScale = min(
            availableWidth / projected.width.coerceAtLeast(1f),
            availableHeight / projected.height.coerceAtLeast(1f)
        ) * transform.scale

        poseStack.translate(
            bounds.x + bounds.width / 2f + transform.offsetX,
            bounds.y + bounds.height / 2f + transform.offsetY,
            0f
        )
        poseStack.scale(-fittedScale, -fittedScale, fittedScale)
        poseStack.translate(-projected.centerX, -projected.centerY, -projected.centerZ)
        rotateAroundPivot(pivot, poseStack, transform)
    }

    private fun projectedBounds(
        blocks: List<PreviewBlock>,
        pivot: Vector3f,
        transform: MultiblockPreviewTransform
    ): ProjectedBounds {
        val rotation = rotation(transform)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        val visibleBlocks = blocks.ifEmpty { listOf(PreviewBlock(BlockPos.ZERO, Blocks.AIR.defaultBlockState())) }

        visibleBlocks.forEach { block ->
            for (x in floatArrayOf(block.pos.x.toFloat(), block.pos.x + 1f)) {
                for (y in floatArrayOf(block.pos.y.toFloat(), block.pos.y + 1f)) {
                    for (z in floatArrayOf(block.pos.z.toFloat(), block.pos.z + 1f)) {
                        val point = Vector3f(x, y, z)
                            .sub(pivot)
                        rotation.transform(point)
                        point.add(pivot)
                        minX = minOf(minX, point.x)
                        minY = minOf(minY, point.y)
                        minZ = minOf(minZ, point.z)
                        maxX = maxOf(maxX, point.x)
                        maxY = maxOf(maxY, point.y)
                        maxZ = maxOf(maxZ, point.z)
                    }
                }
            }
        }

        return ProjectedBounds(minX, minY, minZ, maxX, maxY, maxZ)
    }

    private fun applyWorldTransform(
        multiblock: Multiblock,
        poseStack: PoseStack,
        transform: MultiblockPreviewTransform
    ) {
        poseStack.translate(transform.offsetX, transform.offsetY, 0f)
        poseStack.scale(transform.scale, transform.scale, transform.scale)
        rotateAroundPivot(multiblock, poseStack, transform)
    }

    private fun rotateAroundPivot(
        multiblock: Multiblock,
        poseStack: PoseStack,
        transform: MultiblockPreviewTransform
    ) {
        val pivot = Vector3f(multiblock.xSize / 2f, 0f, multiblock.zSize / 2f)
        rotateAroundPivot(pivot, poseStack, transform)
    }

    private fun rotateAroundPivot(
        pivot: Vector3f,
        poseStack: PoseStack,
        transform: MultiblockPreviewTransform
    ) {
        val rotation = rotation(transform)

        poseStack.translate(pivot.x, pivot.y, pivot.z)
        poseStack.mulPose(rotation)
        poseStack.translate(-pivot.x, -pivot.y, -pivot.z)
    }

    private fun rotation(transform: MultiblockPreviewTransform): Quaternionf = Quaternionf()
        .rotateX(transform.rotationY * Mth.DEG_TO_RAD)
        .rotateY(transform.rotationX * Mth.DEG_TO_RAD)

    private fun submitBlocks(
        multiblock: Multiblock,
        poseStack: PoseStack,
        submitter: SubmitNodeCollector,
        transform: MultiblockPreviewTransform
    ) {
        val blocks = previewBlocks(multiblock, transform)
        submitBlocks(blocks, poseStack, submitter)
    }

    private fun submitBlocks(
        blocks: List<PreviewBlock>,
        poseStack: PoseStack,
        submitter: SubmitNodeCollector,
        outlineColor: Int = -1,
    ) {
        blocks.forEach { block ->
            submitBlock(block, poseStack, submitter, outlineColor)
        }
    }

    private fun previewBlocks(
        definition: AssembledMultiblockDefinition,
        transform: MultiblockPreviewTransform
    ): List<PreviewBlock> {
        val selectedY = definition.minOffset.y + transform.layer.coerceIn(0, definition.ySize - 1)
        return definition.parts.flatMap { part ->
            if (transform.singleLayer && part.offset.y != selectedY) return@flatMap emptyList()
            val state = part.matcher.previewState() ?: return@flatMap emptyList()
            if (state.isAir) return@flatMap emptyList()
            previewParts(part.offset, state)
        }
    }

    private fun submitFormed(
        definition: AssembledMultiblockDefinition,
        poseStack: PoseStack,
        submitter: SubmitNodeCollector,
        bounds: MultiblockPreviewBounds,
        transform: MultiblockPreviewTransform
    ) {
        val model = definition.formedModel ?: return
        val data = BedrockGeoRenderEngine.extract(model, emptyList(), MolangContext.EMPTY, 0.0) ?: return
        val projected = projectedBounds(data, transform)
        val availableWidth = bounds.width.coerceAtLeast(1f)
        val availableHeight = bounds.height.coerceAtLeast(1f)
        val fittedScale = min(
            availableWidth / projected.width.coerceAtLeast(1f),
            availableHeight / projected.height.coerceAtLeast(1f)
        ) * transform.scale
        val pivot = geoPivot(data)

        poseStack.pushPose()
        poseStack.translate(
            bounds.x + bounds.width / 2f + transform.offsetX,
            bounds.y + bounds.height / 2f + transform.offsetY,
            0f
        )
        poseStack.scale(-fittedScale, -fittedScale, fittedScale)
        poseStack.translate(-projected.centerX, -projected.centerY, -projected.centerZ)
        rotateAroundPivot(pivot, poseStack, transform)
        BedrockGeoRenderEngine.submit(data, poseStack, submitter, LightCoordsUtil.FULL_BRIGHT)
        poseStack.popPose()
    }

    private fun projectedBounds(
        data: BedrockGeoRenderData,
        transform: MultiblockPreviewTransform
    ): ProjectedBounds {
        val pivot = geoPivot(data)
        val halfWidth = data.model.visibleBoundsWidth.coerceAtLeast(1f) * data.scale / 2f
        val halfHeight = data.model.visibleBoundsHeight.coerceAtLeast(1f) * data.scale / 2f
        val rotation = rotation(transform)
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var minZ = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var maxZ = Float.NEGATIVE_INFINITY

        for (x in floatArrayOf(pivot.x - halfWidth, pivot.x + halfWidth)) {
            for (y in floatArrayOf(pivot.y - halfHeight, pivot.y + halfHeight)) {
                for (z in floatArrayOf(pivot.z - halfWidth, pivot.z + halfWidth)) {
                    val point = Vector3f(x, y, z).sub(pivot)
                    rotation.transform(point)
                    point.add(pivot)
                    minX = minOf(minX, point.x)
                    minY = minOf(minY, point.y)
                    minZ = minOf(minZ, point.z)
                    maxX = maxOf(maxX, point.x)
                    maxY = maxOf(maxY, point.y)
                    maxZ = maxOf(maxZ, point.z)
                }
            }
        }
        return ProjectedBounds(minX, minY, minZ, maxX, maxY, maxZ)
    }

    private fun geoPivot(data: BedrockGeoRenderData): Vector3f = Vector3f(
        data.model.visibleBoundsOffsetX * data.scale,
        data.model.visibleBoundsOffsetY * data.scale,
        data.model.visibleBoundsOffsetZ * data.scale
    )

    private fun previewBlocks(multiblock: Multiblock, transform: MultiblockPreviewTransform): List<PreviewBlock> {
        val layers = when {
            transform.singleLayer -> {
                val selected = transform.layer.coerceIn(0, multiblock.ySize - 1)
                selected..selected
            }
            transform.layer != Int.MAX_VALUE -> 0..transform.layer.coerceIn(0, multiblock.ySize - 1)
            else -> 0..<multiblock.ySize
        }

        return buildList {
            for (y in layers) {
                for (z in 0..<multiblock.zSize) {
                    for (x in 0..<multiblock.xSize) {
                        val pos = BlockPos(x, y, z)
                        val state = multiblock.getBlockState(pos)
                        if (state.isAir) continue
                        addAll(previewParts(pos, state))
                    }
                }
            }
        }
    }

    private fun previewParts(pos: BlockPos, state: BlockState): List<PreviewBlock> {
        val block = state.block
        if (block is Multipart<*>) {
            return block.getPreviewParts(pos, Direction.NORTH, state)
                .map { (partPos, partState) -> PreviewBlock(partPos, partState) }
        }
        return listOf(PreviewBlock(pos, state))
    }

    private fun submitBlock(
        block: PreviewBlock,
        poseStack: PoseStack,
        submitter: SubmitNodeCollector,
        outlineColor: Int,
    ) {
        val state = block.state
        if (state.isAir) return

        modelState.clear()
        modelResolver.update(modelState, state, displayContext)

        if (modelState.isEmpty) return

        poseStack.pushPose()
        poseStack.translate(block.pos.x.toFloat(), block.pos.y.toFloat(), block.pos.z.toFloat())
        modelState.submit(poseStack, submitter, LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, outlineColor)
        poseStack.popPose()
    }

    private data class PreviewBlock(val pos: BlockPos, val state: BlockState)
}

data class MultiblockPreviewBounds(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float
)

data class MultiblockPreviewTransform(
    val scale: Float = 0.9f,
    val rotationX: Float = 0f,
    val rotationY: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val layer: Int = Int.MAX_VALUE,
    val singleLayer: Boolean = false
)

private data class ProjectedBounds(
    val minX: Float,
    val minY: Float,
    val minZ: Float,
    val maxX: Float,
    val maxY: Float,
    val maxZ: Float
) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = (minX + maxX) / 2f
    val centerY: Float get() = (minY + maxY) / 2f
    val centerZ: Float get() = (minZ + maxZ) / 2f
}
