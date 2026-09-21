package com.algorithmlx.ecr.api.particle.collision

import net.minecraft.core.Direction
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import org.joml.Vector3f
import kotlin.math.abs

class LevelCollisionProvider(private val level: Level) : CollisionProvider {
    override fun query(pos: Vector3f, size: Float, offset: Vector3f): Pair<Vector3f, Vector3f>? = query(
        AABB(
            (pos.x - size).toDouble(), (pos.y - size).toDouble(), (pos.z - size).toDouble(),
            (pos.x + size).toDouble(), (pos.y + size).toDouble(), (pos.z + size).toDouble()
        ),
        offset
    )

    private fun query(aabb: AABB, offset: Vector3f): Pair<Vector3f, Vector3f>? {
        val expandedAABB = aabb.expandTowards(offset.x.toDouble(), offset.y.toDouble(), offset.z.toDouble())
        val collisions = level.getCollisions(null, expandedAABB).toList()
        if (collisions.isEmpty()) return null

        var iter = 0
        var remaining = Vector3f(offset)
        var primaryAxis = selectPrimaryAxis(offset)
        var currentAABB = aabb

        while (iter < 1000) {
            var x = remaining.x.toDouble()
            var y = remaining.y.toDouble()
            var z = remaining.z.toDouble()

            var testAABB = currentAABB

            fun checkXAxis() {
                if (x == 0.0) return
                for (bb in collisions) x = bb.collide(Direction.Axis.X, testAABB, x)
                if (x == 0.0) return
                testAABB = testAABB.expandTowards(x, 0.0, 0.0)
            }

            fun checkYAxis() {
                if (y == 0.0) return
                for (bb in collisions) y = bb.collide(Direction.Axis.Y, testAABB, y)
                if (y == 0.0) return
                testAABB = testAABB.expandTowards(0.0, y, 0.0)
            }

            fun checkZAxis() {
                if (z == 0.0) return
                for (bb in collisions) z = bb.collide(Direction.Axis.Z, testAABB, z)
                if (z == 0.0) return
                testAABB = testAABB.expandTowards(0.0, 0.0, z)
            }

            when (primaryAxis) {
                Axis.X -> {
                    checkXAxis()
                    checkYAxis()
                    checkZAxis()
                }

                Axis.Y -> {
                    checkYAxis()
                    checkXAxis()
                    checkZAxis()
                }

                Axis.Z -> {
                    checkZAxis()
                    checkYAxis()
                    checkXAxis()
                }
            }

            if (x == remaining.x.toDouble() && y == remaining.y.toDouble() && z == remaining.z.toDouble()) {
                return null
            }

            val (minFraction, minAxis) = calculateSafeFraction(x, y, z, remaining)

            if (minFraction <= 0.001 || iter > 3) {
                val axisVec = minAxis.vec
                val normal = if (minAxis.get(offset) > 0) axisVec.negate() else axisVec
                return Vector3f(offset).sub(remaining) to normal
            }

            val safeOffset = Vector3f(remaining).mul(minFraction - 0.0001f)
            remaining = Vector3f(remaining).sub(safeOffset)
            currentAABB = currentAABB.move(safeOffset.x.toDouble(), safeOffset.y.toDouble(), safeOffset.z.toDouble())
            primaryAxis = minAxis

            iter++
        }

        return null
    }

    private fun selectPrimaryAxis(offset: Vector3f): Axis {
        return when {
            abs(offset.x) > abs(offset.y) && abs(offset.x) > abs(offset.z) -> Axis.X
            abs(offset.y) > abs(offset.z) -> Axis.Y
            else -> Axis.Z
        }
    }

    private fun calculateSafeFraction(x: Double, y: Double, z: Double, offset: Vector3f): Pair<Float, Axis> {
        val xFraction = fraction(x, offset.x)
        val yFraction = fraction(y, offset.y)
        val zFraction = fraction(z, offset.z)

        return when {
            xFraction < yFraction && xFraction < zFraction -> xFraction to Axis.X
            yFraction < zFraction -> yFraction to Axis.Y
            else -> zFraction to Axis.Z
        }
    }

    private fun fraction(value: Double, offset: Float) =
        if (offset == 0f) Float.POSITIVE_INFINITY else value.toFloat() / offset

    private enum class Axis {
        X, Y, Z;

        val vec: Vector3f
            get() = when(this) {
                X -> Vector3f(1F, 0F, 0F)
                Y -> Vector3f(0F, 1F, 0F)
                Z -> Vector3f(0F, 0F, 1F)
            }

        fun get(vec: Vector3f): Float = get(vec.x, vec.y, vec.z)

        fun get(x: Float, y: Float, z: Float): Float = when (this) {
            X -> x
            Y -> y
            Z -> z
        }
    }
}
