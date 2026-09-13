package com.algorithmlx.ecr.api.client.texture

import net.minecraft.client.renderer.block.BlockAndTintGetter
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.resources.Identifier
import net.minecraft.world.level.block.state.BlockState
import java.util.*

enum class ConnectedTextureRotation {
    NONE,
    CLOCKWISE_90,
    CLOCKWISE_180,
    CLOCKWISE_270,
}

data class ConnectedTextureVariant @JvmOverloads constructor(
    val sprite: Identifier,
    val rotation: ConnectedTextureRotation = ConnectedTextureRotation.NONE,
    val region: ConnectedTextureRegion? = null,
)

data class ConnectedTextureRegion(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    init {
        require(x >= 0) { "Connected texture region x must not be negative, got $x" }
        require(y >= 0) { "Connected texture region y must not be negative, got $y" }
        require(width > 0) { "Connected texture region width must be positive, got $width" }
        require(height > 0) { "Connected texture region height must be positive, got $height" }
    }
}

fun interface ConnectedTextureConnection {
    fun connects(
        level: BlockAndTintGetter,
        originPos: BlockPos,
        originState: BlockState,
        neighbourPos: BlockPos,
        neighbourState: BlockState,
        face: Direction,
    ): Boolean

    companion object {
        @JvmField
        val SAME_BLOCK = ConnectedTextureConnection { _, _, origin, _, neighbour, _ ->
            origin.`is`(neighbour.block)
        }

        @JvmField
        val SAME_STATE = ConnectedTextureConnection { _, _, origin, _, neighbour, _ ->
            origin === neighbour
        }
    }
}

class ConnectedTexture private constructor(
    val source: Identifier,
    variants: Array<out ConnectedTextureVariant?>,
    val connection: ConnectedTextureConnection = ConnectedTextureConnection.SAME_BLOCK,
    faces: Set<Direction> = ALL_FACES,
    private val outlineComponentBounds: Boolean = false,
) {
    val variants: List<ConnectedTextureVariant?> = Collections.unmodifiableList(variants.toList())
    val faces: Set<Direction> = Collections.unmodifiableSet(
        if (faces.isEmpty()) EnumSet.noneOf(Direction::class.java) else EnumSet.copyOf(faces)
    )

    init {
        require(this.variants.size == ConnectedTextureMask.VARIANT_COUNT) {
            "A connected texture requires exactly ${ConnectedTextureMask.VARIANT_COUNT} variants, " +
                "got ${this.variants.size}"
        }
    }

    @JvmOverloads
    constructor(
        source: Identifier,
        variants: List<Identifier>,
        connection: ConnectedTextureConnection = ConnectedTextureConnection.SAME_BLOCK,
        faces: Set<Direction> = ALL_FACES,
    ) : this(
        source = source,
        variants = variants.map { ConnectedTextureVariant(it) }.toTypedArray(),
        connection = connection,
        faces = faces,
    )

    fun packedMask(
        level: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
    ): Int = ConnectedTextureMask.pack { face -> mask(level, pos, state, face) }

    fun mask(
        level: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        face: Direction,
    ): Int {
        if (face !in faces) return 0

        val rawMask = rawMask(level, pos, state, face)
        val connections = Integer.bitCount(rawMask)
        if (!outlineComponentBounds || connections < 2) return rawMask

        val preferredMask = if (connections == 4) {
            ConnectedTextureMask.fourWayFromDiagonals { firstSide, secondSide ->
                val diagonalPos = pos
                    .relative(ConnectedTextureMask.direction(face, firstSide))
                    .relative(ConnectedTextureMask.direction(face, secondSide))
                val diagonalState = level.getBlockState(diagonalPos)
                connection.connects(level, pos, state, diagonalPos, diagonalState, face)
            }
        } else ConnectedTextureMask.straightThroughThreeWay(rawMask)

        if (preferredMask == ConnectedTextureMask.VARIANT_COUNT - 1) return preferredMask

        return ConnectedTextureMask.withoutRejectedConnections(preferredMask) { side ->
            val neighbourPos = pos.relative(ConnectedTextureMask.direction(face, side))
            val neighbourState = level.getBlockState(neighbourPos)
            val neighbourMask = rawMask(level, neighbourPos, neighbourState, face)
            if (Integer.bitCount(neighbourMask) != 3) true
            else {
                val oppositeBit = 1 shl ((side + 2) % 4)
                ConnectedTextureMask.straightThroughThreeWay(neighbourMask) and oppositeBit != 0
            }
        }
    }

    private fun rawMask(
        level: BlockAndTintGetter,
        pos: BlockPos,
        state: BlockState,
        face: Direction,
    ): Int = ConnectedTextureMask.calculate(face) { offset ->
        val neighbourPos = pos.relative(offset)
        val neighbourState = level.getBlockState(neighbourPos)
        connection.connects(level, pos, state, neighbourPos, neighbourState, face)
    }

    companion object {
        @JvmField
        val ALL_FACES: Set<Direction> = Collections.unmodifiableSet(EnumSet.allOf(Direction::class.java))

        @JvmStatic
        @JvmOverloads
        fun numbered(
            source: Identifier,
            prefix: Identifier,
            connection: ConnectedTextureConnection = ConnectedTextureConnection.SAME_BLOCK,
            faces: Set<Direction> = ALL_FACES,
        ): ConnectedTexture = ConnectedTexture(
            source = source,
            variants = List(ConnectedTextureMask.VARIANT_COUNT) { mask ->
                prefix.withPath("${prefix.path}_$mask")
            },
            connection = connection,
            faces = faces,
        )

        @JvmStatic
        @JvmOverloads
        fun fromMap(
            source: Identifier,
            map: Identifier,
            textureSize: Int,
            connection: ConnectedTextureConnection = ConnectedTextureConnection.SAME_BLOCK,
            faces: Set<Direction> = ALL_FACES,
        ): ConnectedTexture {
            require(textureSize > 0) { "Connected texture size must be positive, got $textureSize" }
            require(textureSize <= Int.MAX_VALUE / MAP_GRID_SIZE) {
                "Connected texture size is too large: $textureSize"
            }

            return ConnectedTexture(
                source = source,
                variants = Array(ConnectedTextureMask.VARIANT_COUNT) { mask ->
                    val column = axisIndex(
                        mask = mask,
                        start = ConnectedTextureMask.RIGHT,
                        end = ConnectedTextureMask.LEFT,
                    )
                    val row = axisIndex(
                        mask = mask,
                        start = ConnectedTextureMask.BOTTOM,
                        end = ConnectedTextureMask.TOP,
                    )
                    ConnectedTextureVariant(
                        sprite = map,
                        region = ConnectedTextureRegion(
                            x = column * textureSize,
                            y = row * textureSize,
                            width = textureSize,
                            height = textureSize,
                        ),
                    )
                },
                connection = connection,
                faces = faces,
            )
        }

        @JvmStatic
        @JvmOverloads
        fun linePattern(
            source: Identifier,
            startLine: Identifier,
            line: Identifier,
            angle: Identifier,
            connection: ConnectedTextureConnection = ConnectedTextureConnection.SAME_BLOCK,
            faces: Set<Direction> = ALL_FACES,
            outlineComponentBounds: Boolean = true,
            endLine: Identifier? = null,
            rightAngle: Identifier? = null,
            downAngle: Identifier? = null,
            downRightAngle: Identifier? = null,
        ): ConnectedTexture {
            val leftEnd = endLine?.let { ConnectedTextureVariant(it) }
                ?: ConnectedTextureVariant(startLine, ConnectedTextureRotation.CLOCKWISE_180)
            val topEnd = endLine?.let {
                ConnectedTextureVariant(it, ConnectedTextureRotation.CLOCKWISE_90)
            } ?: ConnectedTextureVariant(startLine, ConnectedTextureRotation.CLOCKWISE_270)
            val rightCorner = rightAngle?.let { ConnectedTextureVariant(it) }
                ?: ConnectedTextureVariant(angle, ConnectedTextureRotation.CLOCKWISE_90)
            val downRightCorner = downRightAngle?.let { ConnectedTextureVariant(it) }
                ?: ConnectedTextureVariant(angle, ConnectedTextureRotation.CLOCKWISE_180)
            val downCorner = downAngle?.let { ConnectedTextureVariant(it) }
                ?: ConnectedTextureVariant(angle, ConnectedTextureRotation.CLOCKWISE_270)

            return sparse(
                source,
                mapOf(
                    ConnectedTextureMask.RIGHT to ConnectedTextureVariant(startLine),
                    ConnectedTextureMask.BOTTOM to ConnectedTextureVariant(
                        startLine,
                        ConnectedTextureRotation.CLOCKWISE_90,
                    ),
                    ConnectedTextureMask.LEFT to leftEnd,
                    ConnectedTextureMask.TOP to topEnd,
                    ConnectedTextureMask.LEFT or ConnectedTextureMask.RIGHT to
                        ConnectedTextureVariant(line),
                    ConnectedTextureMask.TOP or ConnectedTextureMask.BOTTOM to ConnectedTextureVariant(
                        line,
                        ConnectedTextureRotation.CLOCKWISE_90,
                    ),
                    ConnectedTextureMask.RIGHT or ConnectedTextureMask.BOTTOM to
                        ConnectedTextureVariant(angle),
                    ConnectedTextureMask.BOTTOM or ConnectedTextureMask.LEFT to rightCorner,
                    ConnectedTextureMask.LEFT or ConnectedTextureMask.TOP to downRightCorner,
                    ConnectedTextureMask.TOP or ConnectedTextureMask.RIGHT to downCorner,
                ),
                connection,
                faces,
                outlineComponentBounds,
            )
        }

        @JvmStatic
        @JvmOverloads
        fun sparse(
            source: Identifier,
            variants: Map<Int, ConnectedTextureVariant>,
            connection: ConnectedTextureConnection = ConnectedTextureConnection.SAME_BLOCK,
            faces: Set<Direction> = ALL_FACES,
            outlineComponentBounds: Boolean = false,
        ): ConnectedTexture {
            val resolved = arrayOfNulls<ConnectedTextureVariant>(ConnectedTextureMask.VARIANT_COUNT)
            variants.forEach { (mask, variant) ->
                require(mask in resolved.indices) { "Connected texture mask must be between 0 and 15, got $mask" }
                resolved[mask] = variant
            }
            return ConnectedTexture(
                source,
                resolved,
                connection,
                faces,
                outlineComponentBounds,
            )
        }

        private const val MAP_GRID_SIZE = 4

        private fun axisIndex(mask: Int, start: Int, end: Int): Int = when (mask and (start or end)) {
            0 -> 0
            start -> 1
            start or end -> 2
            end -> 3
            else -> error("Unexpected connected texture axis mask")
        }
    }

}
