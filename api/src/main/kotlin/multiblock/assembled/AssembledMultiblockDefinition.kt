package com.algorithmlx.ecr.api.multiblock.assembled

import com.algorithmlx.ecr.api.geo.GeoModel
import com.algorithmlx.ecr.api.geo.GeoBlockRotation
import com.algorithmlx.ecr.api.geo.GeoLightMode
import com.algorithmlx.ecr.api.geo.GeoRenderType
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.tags.TagKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.shapes.BooleanOp
import net.minecraft.world.phys.shapes.Shapes
import net.minecraft.world.phys.shapes.VoxelShape

fun interface AssembledBlockMatcher {
    fun matches(state: BlockState): Boolean

    fun previewState(): BlockState? = null

    companion object {
        @JvmStatic
        fun state(expected: BlockState): AssembledBlockMatcher = object : AssembledBlockMatcher {
            override fun matches(state: BlockState): Boolean = state == expected
            override fun previewState(): BlockState = expected
        }

        @JvmStatic
        fun block(expected: Block): AssembledBlockMatcher = object : AssembledBlockMatcher {
            override fun matches(state: BlockState): Boolean = state.`is`(expected)
            override fun previewState(): BlockState = expected.defaultBlockState()
        }

        @JvmStatic
        fun tag(expected: TagKey<Block>, preview: BlockState? = null): AssembledBlockMatcher =
            object : AssembledBlockMatcher {
                @Volatile
                private var resolvedPreview: BlockState? = preview

                override fun matches(state: BlockState): Boolean = state.`is`(expected)

                override fun previewState(): BlockState? {
                    resolvedPreview?.let { return it }
                    val resolved = BuiltInRegistries.BLOCK
                        .firstOrNull { block -> block.defaultBlockState().`is`(expected) }
                        ?.defaultBlockState()
                    if (resolved != null) resolvedPreview = resolved
                    return resolved
                }
            }
    }
}

data class AssembledMultiblockPart(
    val offset: BlockPos,
    val matcher: AssembledBlockMatcher,
    val formedShape: VoxelShape = Shapes.block()
)

class AssembledMultiblockDefinition(
    val id: Identifier,
    parts: List<AssembledMultiblockPart>,
    val formedModel: GeoModel? = null,
    val allowAssemblyFromAnyPart: Boolean = false,
    formedModelAnchor: BlockPos = BlockPos.ZERO,
    val formedStructureShape: VoxelShape? = null,
    formedShapeOrigin: BlockPos = formedModelAnchor,
    val requiresJsonDefinition: Boolean = false
) {
    val parts: List<AssembledMultiblockPart> = parts.map { part ->
        part.copy(offset = part.offset.immutable())
    }
    val formedModelAnchor: BlockPos = formedModelAnchor.immutable()
    val formedShapeOrigin: BlockPos = formedShapeOrigin.immutable()
    val minOffset: BlockPos
    val maxOffset: BlockPos
    val xSize: Int
    val ySize: Int
    val zSize: Int

    init {
        require(this.parts.isNotEmpty()) { "Assembled multiblock $id must contain at least one part" }
        require(this.parts.any { part -> part.offset == BlockPos.ZERO }) {
            "Assembled multiblock $id must contain a controller part at [0, 0, 0]"
        }
        require(this.parts.map(AssembledMultiblockPart::offset).toSet().size == this.parts.size) {
            "Assembled multiblock $id contains duplicate part offsets"
        }
        require(this.parts.any { part -> part.offset == this.formedModelAnchor }) {
            "Assembled multiblock $id model anchor $formedModelAnchor does not reference a structure part"
        }

        minOffset = BlockPos(
            this.parts.minOf { part -> part.offset.x },
            this.parts.minOf { part -> part.offset.y },
            this.parts.minOf { part -> part.offset.z }
        )
        maxOffset = BlockPos(
            this.parts.maxOf { part -> part.offset.x },
            this.parts.maxOf { part -> part.offset.y },
            this.parts.maxOf { part -> part.offset.z }
        )
        xSize = maxOffset.x - minOffset.x + 1
        ySize = maxOffset.y - minOffset.y + 1
        zSize = maxOffset.z - minOffset.z + 1
    }

    fun worldPosition(
        controllerPos: BlockPos,
        facing: Direction,
        part: AssembledMultiblockPart
    ): BlockPos? = rotate(part.offset, facing)?.let(controllerPos::offset)

    fun worldPositions(controllerPos: BlockPos, facing: Direction): List<BlockPos> =
        parts.mapNotNull { part -> worldPosition(controllerPos, facing, part) }

    fun controllerPosition(
        partPos: BlockPos,
        facing: Direction,
        part: AssembledMultiblockPart
    ): BlockPos? = rotate(part.offset, facing)?.let(partPos::subtract)

    fun controllerCandidates(partPos: BlockPos, facing: Direction): List<BlockPos> {
        if (!facing.axis.isHorizontal) return emptyList()
        if (!allowAssemblyFromAnyPart) return listOf(partPos.immutable())
        return parts.mapNotNull { part -> controllerPosition(partPos, facing, part) }.distinct()
    }

    fun partAt(controllerPos: BlockPos, facing: Direction, worldPos: BlockPos): AssembledMultiblockPart? =
        parts.firstOrNull { part -> worldPosition(controllerPos, facing, part) == worldPos }

    fun formedShapeAt(controllerPos: BlockPos, facing: Direction, worldPos: BlockPos): VoxelShape =
        formedShapesAt(controllerPos, facing, worldPos)?.part ?: Shapes.block()

    fun formedSelectionShapeAt(controllerPos: BlockPos, facing: Direction, worldPos: BlockPos): VoxelShape =
        formedShapesAt(controllerPos, facing, worldPos)?.selection ?: Shapes.block()

    fun firstMismatch(level: Level, controllerPos: BlockPos, facing: Direction): BlockPos? {
        if (!facing.axis.isHorizontal) return null

        return parts.firstNotNullOfOrNull { part ->
            val worldPos = worldPosition(controllerPos, facing, part) ?: return@firstNotNullOfOrNull null
            worldPos.takeUnless { part.matcher.matches(level.getBlockState(worldPos)) }
        }
    }

    fun matches(level: Level, controllerPos: BlockPos, facing: Direction): Boolean =
        facing.axis.isHorizontal && firstMismatch(level, controllerPos, facing) == null

    private fun formedShapesAt(
        controllerPos: BlockPos,
        facing: Direction,
        worldPos: BlockPos
    ): FormedPartShapes? = formedShapesByFacing[facing]?.get(worldPos.subtract(controllerPos))

    private val formedShapesByFacing: Map<Direction, Map<BlockPos, FormedPartShapes>> by lazy {
        HORIZONTAL_DIRECTIONS.associateWith(::bakeFormedShapes)
    }

    private fun bakeFormedShapes(facing: Direction): Map<BlockPos, FormedPartShapes> {
        val rotatedParts = parts.associate { part ->
            val offset = requireNotNull(rotate(part.offset, facing))
            val shape = requireNotNull(rotateShape(part.formedShape, facing))
            offset to shape
        }
        val structureShape = formedStructureShape?.let { shape ->
            val origin = requireNotNull(rotate(formedShapeOrigin, facing))
            requireNotNull(rotateShape(shape, facing)).move(
                origin.x.toDouble(),
                origin.y.toDouble(),
                origin.z.toDouble()
            )
        } ?: rotatedParts.entries.fold(Shapes.empty()) { result, (offset, shape) ->
            Shapes.or(
                result,
                shape.move(offset.x.toDouble(), offset.y.toDouble(), offset.z.toDouble())
            )
        }.optimize()

        return rotatedParts.mapValues { (offset, partShape) ->
            FormedPartShapes(
                part = partShape,
                selection = structureShape.move(
                    -offset.x.toDouble(),
                    -offset.y.toDouble(),
                    -offset.z.toDouble()
                )
            )
        }
    }

    private data class FormedPartShapes(
        val part: VoxelShape,
        val selection: VoxelShape
    )

    companion object {
        @JvmStatic
        fun jsonOnly(id: Identifier): AssembledMultiblockDefinition =
            AssembledMultiblockDefinition(
                id,
                listOf(
                    AssembledMultiblockPart(
                        BlockPos.ZERO,
                        AssembledBlockMatcher.block(Blocks.AIR)
                    )
                ),
                requiresJsonDefinition = true
            )

        @JvmStatic
        fun rotate(offset: BlockPos, facing: Direction): BlockPos? =
            when (facing.horizontalRotation()) {
                HorizontalRotation.NONE -> offset
                HorizontalRotation.CLOCKWISE_90 -> BlockPos(-offset.z, offset.y, offset.x)
                HorizontalRotation.CLOCKWISE_180 -> BlockPos(-offset.x, offset.y, -offset.z)
                HorizontalRotation.COUNTERCLOCKWISE_90 -> BlockPos(offset.z, offset.y, -offset.x)
                null -> null
            }

        @JvmStatic
        fun rotateShape(shape: VoxelShape, facing: Direction): VoxelShape? {
            val rotation = facing.horizontalRotation() ?: return null
            if (rotation == HorizontalRotation.NONE || shape.isEmpty) return shape

            var result: VoxelShape = Shapes.empty()
            shape.forAllBoxes { minX, minY, minZ, maxX, maxY, maxZ ->
                val corners = arrayOf(
                    rotateHorizontal(minX, minZ, rotation),
                    rotateHorizontal(minX, maxZ, rotation),
                    rotateHorizontal(maxX, minZ, rotation),
                    rotateHorizontal(maxX, maxZ, rotation)
                )
                val rotatedMinX = corners.minOf(Pair<Double, Double>::first)
                val rotatedMaxX = corners.maxOf(Pair<Double, Double>::first)
                val rotatedMinZ = corners.minOf(Pair<Double, Double>::second)
                val rotatedMaxZ = corners.maxOf(Pair<Double, Double>::second)
                result = Shapes.or(
                    result,
                    Shapes.box(rotatedMinX, minY, rotatedMinZ, rotatedMaxX, maxY, rotatedMaxZ)
                )
            }
            return result.optimize()
        }

        private fun rotateHorizontal(
            x: Double,
            z: Double,
            rotation: HorizontalRotation
        ): Pair<Double, Double> = when (rotation) {
            HorizontalRotation.NONE -> x to z
            HorizontalRotation.CLOCKWISE_90 -> 1.0 - z to x
            HorizontalRotation.CLOCKWISE_180 -> 1.0 - x to 1.0 - z
            HorizontalRotation.COUNTERCLOCKWISE_90 -> z to 1.0 - x
        }

        private fun Direction.horizontalRotation(): HorizontalRotation? = when (this) {
            Direction.NORTH -> HorizontalRotation.NONE
            Direction.EAST -> HorizontalRotation.CLOCKWISE_90
            Direction.SOUTH -> HorizontalRotation.CLOCKWISE_180
            Direction.WEST -> HorizontalRotation.COUNTERCLOCKWISE_90
            else -> null
        }

        private enum class HorizontalRotation {
            NONE,
            CLOCKWISE_90,
            CLOCKWISE_180,
            COUNTERCLOCKWISE_90
        }

        private val HORIZONTAL_DIRECTIONS = listOf(
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
        )
    }
}

class AssembledMultiblockBuilder internal constructor() {
    private val parts = arrayListOf<AssembledMultiblockPart>()
    private var formedModel: GeoModel? = null
    private var formedModelAnchor: BlockPos = BlockPos.ZERO
    private var unifiedFormedShape: VoxelShape? = null
    private var unifiedFormedShapeOrigin: BlockPos? = null

    fun controller(matcher: AssembledBlockMatcher, formedShape: VoxelShape = Shapes.block()) {
        part(BlockPos.ZERO, matcher, formedShape)
    }

    fun part(
        x: Int,
        y: Int,
        z: Int,
        matcher: AssembledBlockMatcher,
        formedShape: VoxelShape = Shapes.block()
    ) {
        part(BlockPos(x, y, z), matcher, formedShape)
    }

    fun part(
        offset: BlockPos,
        matcher: AssembledBlockMatcher,
        formedShape: VoxelShape = Shapes.block()
    ) {
        parts += AssembledMultiblockPart(offset.immutable(), matcher, formedShape)
    }

    fun formedModel(model: GeoModel) {
        check(formedModel == null) { "Assembled multiblock formed model is already configured" }
        formedModel = model
    }

    fun formedModelAnchor(offset: BlockPos) {
        formedModelAnchor = offset.immutable()
    }

    fun formedModelAnchor(x: Int, y: Int, z: Int) {
        formedModelAnchor(BlockPos(x, y, z))
    }

    fun formedShape(shape: VoxelShape) {
        setUnifiedFormedShape(shape, null)
    }

    fun formedShape(shape: VoxelShape, origin: BlockPos) {
        setUnifiedFormedShape(shape, origin.immutable())
    }

    fun formedShape(shape: VoxelShape, originX: Int, originY: Int, originZ: Int) {
        formedShape(shape, BlockPos(originX, originY, originZ))
    }

    fun formedModel(
        geometry: String,
        texture: Identifier,
        renderType: GeoRenderType = GeoRenderType.CUTOUT,
        scale: Float = 1F,
        lightMode: GeoLightMode = GeoLightMode.WORLD,
        blockRotation: GeoBlockRotation = GeoBlockRotation.NONE
    ) {
        formedModel(
            GeoModel(
                geometry,
                texture,
                renderType,
                scale,
                lightMode = lightMode,
                blockRotation = blockRotation
            )
        )
    }

    fun formedModel(
        geometry: Identifier,
        texture: Identifier,
        renderType: GeoRenderType = GeoRenderType.CUTOUT,
        scale: Float = 1F,
        lightMode: GeoLightMode = GeoLightMode.WORLD,
        blockRotation: GeoBlockRotation = GeoBlockRotation.NONE
    ) {
        formedModel(
            GeoModel(
                geometry,
                texture,
                renderType,
                scale,
                lightMode = lightMode,
                blockRotation = blockRotation
            )
        )
    }

    internal fun build(id: Identifier, allowAssemblyFromAnyPart: Boolean): AssembledMultiblockDefinition {
        val shape = unifiedFormedShape
        val builtParts = if (shape == null) {
            parts
        } else {
            val origin = unifiedFormedShapeOrigin ?: formedModelAnchor
            parts.map { part -> part.copy(formedShape = sliceShape(shape, origin, part.offset)) }
        }
        return AssembledMultiblockDefinition(
            id,
            builtParts,
            formedModel,
            allowAssemblyFromAnyPart,
            formedModelAnchor,
            shape,
            unifiedFormedShapeOrigin ?: formedModelAnchor
        )
    }

    private fun setUnifiedFormedShape(shape: VoxelShape, origin: BlockPos?) {
        check(unifiedFormedShape == null) { "Assembled multiblock unified formed shape is already configured" }
        unifiedFormedShape = shape
        unifiedFormedShapeOrigin = origin
    }

    private fun sliceShape(shape: VoxelShape, origin: BlockPos, partOffset: BlockPos): VoxelShape {
        val relative = partOffset.subtract(origin)
        val cell = Shapes.box(
            relative.x.toDouble(),
            relative.y.toDouble(),
            relative.z.toDouble(),
            relative.x + 1.0,
            relative.y + 1.0,
            relative.z + 1.0
        )
        return Shapes.join(shape, cell, BooleanOp.AND)
            .move(-relative.x.toDouble(), -relative.y.toDouble(), -relative.z.toDouble())
            .optimize()
    }
}

fun assembledMultiblock(
    id: Identifier,
    allowAssemblyFromAnyPart: Boolean = false,
    init: AssembledMultiblockBuilder.() -> Unit
): AssembledMultiblockDefinition =
    AssembledMultiblockBuilder().apply(init).build(id, allowAssemblyFromAnyPart)
