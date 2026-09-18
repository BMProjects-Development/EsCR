package com.algorithmlx.ecr.api.multiblock

import com.mojang.serialization.Codec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.TagKey
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import kotlin.math.abs

open class Multiblock(
    xSize: Int,
    zSize: Int,
    ySize: Int,
    block: Multiblock.() -> Unit
) {
    constructor(xSize: Int, zSize: Int, ySize: Int, blocks: List<MultiblockMatcher>): this(
        xSize,
        zSize,
        ySize,
        { pattern(*blocks.toTypedArray()) }
    )

    companion object {
        private val HORIZONTAL_DIRECTIONS =
            listOf(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)

        @JvmField
        val CODEC: Codec<Multiblock> = RecordCodecBuilder.create {
            it.group(
                Codec.INT.fieldOf("x_size").forGetter(Multiblock::xSize),
                Codec.INT.fieldOf("z_size").forGetter(Multiblock::zSize),
                Codec.INT.fieldOf("y_size").forGetter(Multiblock::ySize),
                MultiblockMatcher.CODEC.listOf().fieldOf("blocks")
                    .forGetter { mb -> mb.blocks },
                MultiblockPattern.CODEC.listOf().optionalFieldOf("variants", emptyList())
                    .forGetter { mb -> if (mb.variants.size > 1) mb.variants else emptyList() }
            ).apply(it) { decodedXSize, decodedZSize, decodedYSize, blocks, variants ->
                Multiblock(decodedXSize, decodedZSize, decodedYSize, blocks).also { multiblock ->
                    if (variants.isNotEmpty()) multiblock.setVariants(variants)
                }
            }
        }

        @JvmStatic
        fun jsonOnly(): Multiblock = object : Multiblock(1, 1, 1, {
            pattern(empty())
        }) {
            override val requiresJsonDefinition: Boolean = true
        }
    }

    open val requiresJsonDefinition: Boolean = false

    private val maximumXSize = xSize
    private val maximumZSize = zSize
    private val maximumYSize = ySize
    private val patternVariants = arrayListOf<MultiblockPattern>()
    val blocks = arrayListOf<MultiblockMatcher>()
    val variants: List<MultiblockPattern> get() = patternVariants

    val xSize: Int get() = previewVariant?.xSize ?: maximumXSize
    val zSize: Int get() = previewVariant?.zSize ?: maximumZSize
    val ySize: Int get() = previewVariant?.ySize ?: maximumYSize

    private val maximumVolume = maximumXSize * maximumYSize * maximumZSize
    private val previewVariant: MultiblockPattern? get() = patternVariants.firstOrNull()

    init { block() }

    fun pattern(vararg blocks: MultiblockMatcher?) {
        require(blocks.size == maximumVolume) {
            "Blocks must have the same size: expected $maximumVolume got ${blocks.size}"
        }

        setVariants(
            listOf(
                MultiblockPattern(
                    maximumXSize,
                    maximumZSize,
                    maximumYSize,
                    blocks.map { it ?: empty() }
                )
            )
        )
    }

    fun scalablePattern(
        radii: IntRange,
        matcher: ScalablePatternContext.() -> MultiblockMatcher?
    ) {
        require(!radii.isEmpty() && radii.first >= 0) { "Scalable pattern radii must be non-empty and non-negative" }

        val maximumDiameter = radii.last * 2 + 1
        require(
            maximumDiameter <= maximumXSize &&
                maximumDiameter <= maximumZSize &&
                maximumDiameter <= maximumYSize
        ) {
            "Scalable pattern diameter $maximumDiameter exceeds multiblock bounds " +
                "($maximumXSize, $maximumYSize, $maximumZSize)"
        }

        val variants = radii.map { outerRadius ->
            val size = outerRadius * 2 + 1
            val blocks = ArrayList<MultiblockMatcher>(size * size * size)

            for (y in 0..<size) {
                for (z in 0..<size) {
                    for (x in 0..<size) {
                        val context = ScalablePatternContext(outerRadius, x, y, z)
                        blocks += context.matcher() ?: empty()
                    }
                }
            }

            MultiblockPattern(size, size, size, blocks)
        }

        setVariants(variants)
    }

    fun matchesIn(level: Level, placement: MultiblockPlacement, block: Block): List<MultiblockWorldMatch> {
        val variant = patternVariants.getOrNull(placement.variantIndex) ?: return emptyList()

        return buildList {
            variant.matchesFor(block).forEach { expected ->
                val structurePos = expected.position
                val worldPos = getRotatedPos(
                    placement.basePos,
                    structurePos.x - placement.startX,
                    structurePos.y - placement.startY,
                    structurePos.z - placement.startZ,
                    placement.direction
                )
                val state = level.getBlockState(worldPos)

                add(
                    MultiblockWorldMatch(
                        placement.variantIndex,
                        structurePos,
                        worldPos,
                        expected.matcher,
                        expected.states,
                        block,
                        state
                    )
                )
            }
        }
    }

    fun countMatchesIn(level: Level, placement: MultiblockPlacement, block: Block): Int {
        val variant = patternVariants.getOrNull(placement.variantIndex) ?: return 0

        return variant.matchesFor(block).count { expected ->
            val structurePos = expected.position
            val worldPos = getRotatedPos(
                placement.basePos,
                structurePos.x - placement.startX,
                structurePos.y - placement.startY,
                structurePos.z - placement.startZ,
                placement.direction
            )

            level.getBlockState(worldPos) in expected.states
        }
    }

    fun findPlacement(level: Level, basePos: BlockPos): MultiblockPlacement? {
        val baseState = level.getBlockState(basePos)

        for (direction in HORIZONTAL_DIRECTIONS) {
            for ((variantIndex, variant) in patternVariants.withIndex()) {
                for ((blockIndex, matcher) in variant.blocks.withIndex()) {
                    if (!matcher.required || !matcher.matches(baseState)) continue

                    val candidate = variant.positionOf(blockIndex)

                    if (
                        checkFromBase(
                            level,
                            basePos,
                            direction,
                            candidate.x,
                            candidate.y,
                            candidate.z,
                            variant
                        )
                    ) {
                        return MultiblockPlacement(
                            basePos,
                            direction,
                            candidate.x,
                            candidate.y,
                            candidate.z,
                            variantIndex
                        )
                    }
                }
            }
        }

        return null
    }

    fun findPlacement(
        level: Level,
        basePos: BlockPos,
        direction: Direction,
        structurePositions: (MultiblockPattern) -> Sequence<BlockPos>
    ): MultiblockPlacement? {
        val baseState = level.getBlockState(basePos)

        for ((variantIndex, variant) in patternVariants.withIndex()) {
            for (structurePos in structurePositions(variant)) {
                if (!variant.contains(structurePos)) continue

                val matcher = variant[structurePos.x, structurePos.y, structurePos.z]
                if (!matcher.required || !matcher.matches(baseState)) continue

                if (
                    checkFromBase(
                        level,
                        basePos,
                        direction,
                        structurePos.x,
                        structurePos.y,
                        structurePos.z,
                        variant
                    )
                ) {
                    return MultiblockPlacement(
                        basePos,
                        direction,
                        structurePos.x,
                        structurePos.y,
                        structurePos.z,
                        variantIndex
                    )
                }
            }
        }

        return null
    }

    fun findPlacement(level: Level, basePos: BlockPos, structurePos: BlockPos): MultiblockPlacement? =
        findPlacement(level, basePos) { structurePos }

    fun findPlacementAtCenter(level: Level, basePos: BlockPos): MultiblockPlacement? =
        findPlacement(level, basePos, MultiblockPattern::center)

    fun findPlacement(
        level: Level,
        basePos: BlockPos,
        structurePos: (MultiblockPattern) -> BlockPos
    ): MultiblockPlacement? {
        for (direction in HORIZONTAL_DIRECTIONS) {
            for ((variantIndex, variant) in patternVariants.withIndex()) {
                val variantStructurePos = structurePos(variant)
                if (!variant.contains(variantStructurePos)) continue

                if (
                    checkFromBase(level, basePos, direction, variantStructurePos.x, variantStructurePos.y, variantStructurePos.z, variant)
                ) {
                    return MultiblockPlacement(
                        basePos,
                        direction,
                        variantStructurePos.x,
                        variantStructurePos.y,
                        variantStructurePos.z,
                        variantIndex
                    )
                }
            }
        }

        return null
    }

    fun replaceInWorld(
        level: Level,
        placement: MultiblockPlacement,
        replaceAir: Boolean = false,
        transform: (BlockState) -> BlockState
    ) {
        val variant = patternVariants.getOrNull(placement.variantIndex) ?: return

        (0 ..< variant.ySize).forEach { y ->
            (0 ..< variant.zSize).forEach { z ->
                (0 ..< variant.xSize).forEach fe@{ x ->
                    val oldDefaultState = variant[x, y, z].default()

                    if (!replaceAir && oldDefaultState.isAir) return@fe

                    val newState = transform(oldDefaultState)
                    val pos = getRotatedPos(
                        placement.basePos,
                        x - placement.startX, y - placement.startY, z - placement.startZ,
                        placement.direction
                    )

                    level.setBlock(pos, newState, Block.UPDATE_ALL)
                }
            }
        }
    }

    fun replaceCurrentBlocks(
        level: Level,
        placement: MultiblockPlacement,
        transform: (BlockState) -> BlockState,
        replaceAir: Boolean
    ) {
        val variant = patternVariants.getOrNull(placement.variantIndex) ?: return

        (0 ..< variant.ySize).forEach { y ->
            (0 ..< variant.zSize).forEach { z ->
                (0 ..< variant.xSize).forEach fe@{ x ->
                    val matcher = variant[x, y, z]

                    if (!replaceAir && matcher.default().isAir) return@fe

                    val pos = getRotatedPos(
                        placement.basePos,
                        x - placement.startX, y - placement.startY, z - placement.startZ,
                        placement.direction
                    )

                    val currentState = level.getBlockState(pos)

                    if (!matcher.matches(currentState)) return@fe

                    level.setBlock(pos, transform(currentState), Block.UPDATE_ALL)
                }
            }
        }
    }

    fun matches(level: Level, placement: MultiblockPlacement): Boolean {
        val variant = patternVariants.getOrNull(placement.variantIndex) ?: return false
        return checkFromBase(
            level,
            placement.basePos,
            placement.direction,
            placement.startX,
            placement.startY,
            placement.startZ,
            variant
        )
    }

    private fun checkFromBase(
        level: Level,
        basePos: BlockPos,
        direction: Direction,
        startX: Int,
        startY: Int,
        startZ: Int,
        variant: MultiblockPattern
    ): Boolean {
        var index = 0
        for (y in 0..<variant.ySize) {
            for (z in 0..<variant.zSize) {
                for (x in 0..<variant.xSize) {
                    val matcher = variant.blocks[index++]
                    if (!matcher.required) continue

                    val rotatedPos = getRotatedPos(basePos, x - startX, y - startY, z - startZ, direction)
                    val currentBlock = level.getBlockState(rotatedPos)

                    if (!matcher.matches(currentBlock)) return false
                }
            }
        }

        return true
    }

    private fun setVariants(variants: List<MultiblockPattern>) {
        require(variants.isNotEmpty()) { "Multiblock must contain at least one pattern variant" }
        variants.forEach { variant ->
            require(
                variant.xSize <= maximumXSize &&
                    variant.zSize <= maximumZSize &&
                    variant.ySize <= maximumYSize
            ) {
                "Pattern variant (${variant.xSize}, ${variant.ySize}, ${variant.zSize}) exceeds " +
                    "multiblock bounds ($maximumXSize, $maximumYSize, $maximumZSize)"
            }
        }

        patternVariants.clear()
        patternVariants += variants.sortedByDescending(MultiblockPattern::volume)

        blocks.clear()
        blocks += requireNotNull(previewVariant).blocks
    }

    private fun getRotatedPos(basePos: BlockPos, x: Int, y: Int, z: Int, direction: Direction): BlockPos {
        return when (direction) {
            Direction.NORTH -> basePos.offset(x, y, -z)
            Direction.SOUTH -> basePos.offset(-x, y, z)
            Direction.WEST -> basePos.offset(-z, y, -x)
            Direction.EAST -> basePos.offset(z, y, x)
            else -> basePos
        }
    }

    fun getBlockState(pos: BlockPos): BlockState {
        val variant = previewVariant ?: return Blocks.LIGHT.defaultBlockState()
        if (!variant.contains(pos)) return Blocks.LIGHT.defaultBlockState()
        return variant[pos.x, pos.y, pos.z].default()
    }

    fun empty() = BlockMultiblockMatcher(Blocks.AIR.defaultBlockState(), required = false)
    fun block(state: BlockState, ignoreTag: Boolean = false) = BlockMultiblockMatcher(state, ignoreTag)
    fun tag(tag: TagKey<Block>) = TagMultiblockMatcher(tag)
    fun list(vararg matchers: MultiblockMatcher, defaultState: BlockState? = null) = ListMultiblockMatcher(matchers.toList(), defaultState)
    fun list(matchers: List<MultiblockMatcher>, defaultState: BlockState? = null) = ListMultiblockMatcher(matchers, defaultState)
}

data class MultiblockPlacement(
    val basePos: BlockPos,
    val direction: Direction,
    val startX: Int,
    val startY: Int,
    val startZ: Int,
    val variantIndex: Int = 0
)

data class MultiblockPattern(
    val xSize: Int,
    val zSize: Int,
    val ySize: Int,
    val blocks: List<MultiblockMatcher>
) {
    val volume: Int = xSize * ySize * zSize
    val center: BlockPos = BlockPos(xSize / 2, ySize / 2, zSize / 2)
    private val matchesByBlock = hashMapOf<Block, List<MultiblockPatternMatch>>()

    init {
        require(xSize > 0 && zSize > 0 && ySize > 0) { "Multiblock pattern dimensions must be positive" }
        require(blocks.size == volume) {
            "Blocks must have the same size as the pattern: expected $volume got ${blocks.size}"
        }
    }

    operator fun get(x: Int, y: Int, z: Int): MultiblockMatcher =
        blocks[x + z * xSize + y * xSize * zSize]

    fun contains(pos: BlockPos): Boolean =
        pos.x in 0..<xSize && pos.y in 0..<ySize && pos.z in 0..<zSize

    fun positionOf(index: Int): BlockPos {
        require(index in blocks.indices) { "Block index $index is outside pattern bounds" }

        val layerSize = xSize * zSize
        val y = index / layerSize
        val layerIndex = index % layerSize
        val z = layerIndex / xSize
        val x = layerIndex % xSize
        return BlockPos(x, y, z)
    }

    fun matchesFor(block: Block): List<MultiblockPatternMatch> = matchesByBlock.getOrPut(block) {
        val possibleStates = block.stateDefinition.possibleStates

        blocks.mapIndexedNotNull { index, matcher ->
            if (!matcher.required) return@mapIndexedNotNull null

            val states = possibleStates.filter(matcher::matches)
            if (states.isEmpty()) null
            else MultiblockPatternMatch(positionOf(index), matcher, states)
        }
    }

    companion object {
        @JvmField
        val CODEC: Codec<MultiblockPattern> = RecordCodecBuilder.create {
            it.group(
                Codec.INT.fieldOf("x_size").forGetter(MultiblockPattern::xSize),
                Codec.INT.fieldOf("z_size").forGetter(MultiblockPattern::zSize),
                Codec.INT.fieldOf("y_size").forGetter(MultiblockPattern::ySize),
                MultiblockMatcher.CODEC.listOf().fieldOf("blocks")
                    .forGetter(MultiblockPattern::blocks)
            ).apply(it, ::MultiblockPattern)
        }
    }
}

data class MultiblockPatternMatch(
    val position: BlockPos,
    val matcher: MultiblockMatcher,
    val states: List<BlockState>
)

data class MultiblockWorldMatch(
    val variantIndex: Int,
    val structurePos: BlockPos,
    val worldPos: BlockPos,
    val matcher: MultiblockMatcher,
    val expectedStates: List<BlockState>,
    val block: Block,
    val state: BlockState
) {
    val isPresent: Boolean get() = state.`is`(block)
    val matches: Boolean get() = state in expectedStates
}

class ScalablePatternContext internal constructor(
    val outerRadius: Int,
    val x: Int,
    val y: Int,
    val z: Int
) {
    val size: Int = outerRadius * 2 + 1
    val centerCoordinate: Int = outerRadius
    val relativeX: Int = x - centerCoordinate
    val relativeY: Int = y - centerCoordinate
    val relativeZ: Int = z - centerCoordinate
    val xRadius: Int = abs(relativeX)
    val yRadius: Int = abs(relativeY)
    val zRadius: Int = abs(relativeZ)
    val horizontalRadius: Int = maxOf(xRadius, zRadius)
    val radius: Int = maxOf(horizontalRadius, yRadius)
    val boundaryAxisCount: Int =
        (if (xRadius == outerRadius) 1 else 0) +
            (if (yRadius == outerRadius) 1 else 0) +
            (if (zRadius == outerRadius) 1 else 0)
    val isCenter: Boolean = radius == 0
    val isBoundary: Boolean = boundaryAxisCount > 0
    val isEdge: Boolean = boundaryAxisCount >= 2
    val position: BlockPos get() = BlockPos(x, y, z)
}
