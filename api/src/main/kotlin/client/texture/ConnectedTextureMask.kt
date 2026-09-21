package com.algorithmlx.ecr.api.client.texture

import net.minecraft.core.Direction

object ConnectedTextureMask {
    const val TOP: Int = 1
    const val RIGHT: Int = 2
    const val BOTTOM: Int = 4
    const val LEFT: Int = 8
    const val VARIANT_COUNT: Int = 16

    private val neighbours = arrayOf(
        arrayOf(Direction.SOUTH, Direction.EAST, Direction.NORTH, Direction.WEST), // DOWN
        arrayOf(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST), // UP
        arrayOf(Direction.UP, Direction.WEST, Direction.DOWN, Direction.EAST), // NORTH
        arrayOf(Direction.UP, Direction.EAST, Direction.DOWN, Direction.WEST), // SOUTH
        arrayOf(Direction.UP, Direction.SOUTH, Direction.DOWN, Direction.NORTH), // WEST
        arrayOf(Direction.UP, Direction.NORTH, Direction.DOWN, Direction.SOUTH), // EAST
    )

    @JvmStatic
    fun direction(face: Direction, side: Int): Direction {
        require(side in 0..3) { "Connected texture side must be between 0 and 3, got $side" }
        return neighbours[face.get3DDataValue()][side]
    }

    @JvmStatic
    fun calculate(face: Direction, connects: (Direction) -> Boolean): Int {
        var mask = 0
        for (side in 0..3) {
            if (connects(direction(face, side))) {
                mask = mask or (1 shl side)
            }
        }
        return mask
    }

    internal fun straightThroughThreeWay(mask: Int): Int {
        if (Integer.bitCount(mask) != 3) return mask
        val missingSide = (0..3).first { mask and (1 shl it) == 0 }
        return mask and (1 shl ((missingSide + 2) % 4)).inv()
    }

    internal fun fourWayFromDiagonals(
        hasDiagonal: (firstSide: Int, secondSide: Int) -> Boolean
    ): Int {
        var missingCorners = 0
        for (side in 0..3) {
            val nextSide = (side + 1) % 4
            if (!hasDiagonal(side, nextSide)) {
                missingCorners = missingCorners or (1 shl side)
            }
        }

        if (Integer.bitCount(missingCorners) == 1) {
            val corner = Integer.numberOfTrailingZeros(missingCorners)
            return (1 shl corner) or (1 shl ((corner + 1) % 4))
        }

        return when (missingCorners) {
            0b1001, 0b0110 -> LEFT or RIGHT
            0b0011, 0b1100 -> TOP or BOTTOM
            else -> VARIANT_COUNT - 1
        }
    }

    internal fun withoutRejectedConnections(
        mask: Int,
        accepts: (side: Int) -> Boolean
    ): Int {
        var result = mask
        for (side in 0..3) {
            val bit = 1 shl side
            if (result and bit != 0 && !accepts(side)) {
                result = result and bit.inv()
            }
        }
        return result
    }

    @JvmStatic
    fun pack(mask: (Direction) -> Int): Int {
        var packed = 0
        for (face in Direction.entries) {
            packed = packed or ((mask(face) and 0xF) shl (face.get3DDataValue() * 4))
        }
        return packed
    }

    @JvmStatic
    fun unpack(packed: Int, face: Direction): Int =
        (packed ushr (face.get3DDataValue() * 4)) and 0xF
}
