package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.geo.client.BedrockGeoAnimator
import com.algorithmlx.ecr.api.geo.client.BedrockGeoAssets
import com.algorithmlx.ecr.api.geo.client.BakedGeoModel
import com.algorithmlx.ecr.api.molang.runtime.MolangContext
import net.minecraft.resources.Identifier
import org.joml.Vector3f
import kotlin.random.Random
import kotlin.math.sqrt

internal class VfxModelSurface private constructor(private val triangles: List<Triangle>, private val totalArea: Float) {
    private data class Triangle(val a: Vector3f, val b: Vector3f, val c: Vector3f, val normal: Vector3f, val areaEnd: Float)

    fun sample(random: Random): Pair<Vector3f, Vector3f> {
        val selected = random.nextFloat() * totalArea
        var low = 0
        var high = triangles.lastIndex
        while (low < high) {
            val middle = (low + high) ushr 1
            if (selected <= triangles[middle].areaEnd) high = middle else low = middle + 1
        }
        val triangle = triangles[low]
        val a = sqrt(random.nextFloat())
        val b = random.nextFloat()
        val point = Vector3f(triangle.a).mul(1f - a)
            .fma(a * (1f - b), triangle.b)
            .fma(a * b, triangle.c)
        return point to Vector3f(triangle.normal)
    }

    companion object {
        fun load(name: String): VfxModelSurface? {
            val baked = if (name.startsWith("geometry.")) BedrockGeoAssets[name]
                else Identifier.tryParse(name)?.let(BedrockGeoAssets::get) ?: BedrockGeoAssets[name]
            return baked?.let(::from)
        }

        fun from(baked: BakedGeoModel): VfxModelSurface? {
            val pose = BedrockGeoAnimator.pose(baked, emptyList(), MolangContext.EMPTY, 0.0)
            val triangles = arrayListOf<Triangle>()
            var totalArea = 0f
            baked.bones.forEachIndexed { boneIndex, bone ->
                bone.quads.forEach { quad ->
                    val vertices = Array(4) { index ->
                        val offset = index * 3
                        pose.transforms[boneIndex].transformPosition(Vector3f(quad.positions[offset], quad.positions[offset + 1], quad.positions[offset + 2]))
                    }
                    listOf(intArrayOf(0, 1, 2), intArrayOf(0, 2, 3)).forEach { indices ->
                        val a = vertices[indices[0]]
                        val b = vertices[indices[1]]
                        val c = vertices[indices[2]]
                        val normal = Vector3f(b).sub(a).cross(Vector3f(c).sub(a))
                        val area = normal.length() * 0.5f
                        if (area > 0.000001f) {
                            totalArea += area
                            triangles += Triangle(a, b, c, normal.normalize(), totalArea)
                        }
                    }
                }
            }
            return if (triangles.isEmpty()) null else VfxModelSurface(triangles, totalArea)
        }
    }
}
