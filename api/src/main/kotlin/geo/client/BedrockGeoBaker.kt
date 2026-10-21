package com.algorithmlx.ecr.api.geo.client

import com.algorithmlx.ecr.api.geo.file.BedrockBone
import com.algorithmlx.ecr.api.geo.file.BedrockCube
import com.algorithmlx.ecr.api.geo.file.BedrockGeometry
import com.algorithmlx.ecr.api.geo.file.BedrockUv
import com.algorithmlx.ecr.api.geo.file.GeoFaceDirection
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.PI

data class BakedGeoModel(
    val identifier: String,
    val visibleBoundsWidth: Float,
    val visibleBoundsHeight: Float,
    val visibleBoundsOffsetX: Float,
    val visibleBoundsOffsetY: Float,
    val visibleBoundsOffsetZ: Float,
    val bones: List<BakedGeoBone>,
    val boneIndices: Map<String, Int>
)

data class BakedGeoBone(
    val name: String,
    val parentIndex: Int,
    val pivotX: Float,
    val pivotY: Float,
    val pivotZ: Float,
    val rotationX: Float,
    val rotationY: Float,
    val rotationZ: Float,
    val quads: List<BakedGeoQuad>
)

data class BakedGeoQuad(
    val positions: FloatArray,
    val uvs: FloatArray,
    val normalX: Float,
    val normalY: Float,
    val normalZ: Float
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BakedGeoQuad

        if (normalX != other.normalX) return false
        if (normalY != other.normalY) return false
        if (normalZ != other.normalZ) return false
        if (!positions.contentEquals(other.positions)) return false
        if (!uvs.contentEquals(other.uvs)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = normalX.hashCode()
        result = 31 * result + normalY.hashCode()
        result = 31 * result + normalZ.hashCode()
        result = 31 * result + positions.contentHashCode()
        result = 31 * result + uvs.contentHashCode()
        return result
    }
}

object BedrockGeoBaker {
    private const val MODEL_UNITS = 16F

    fun bake(geometry: BedrockGeometry): BakedGeoModel {
        val sortedBones = topologicalBones(geometry.bones)
        val indices = sortedBones.mapIndexed { index, bone -> bone.name to index }.toMap()
        val bones = sortedBones.map { bone ->
            BakedGeoBone(
                bone.name,
                bone.parent?.let { indices[it] } ?: -1,
                -bone.pivot.x / MODEL_UNITS,
                bone.pivot.y / MODEL_UNITS,
                bone.pivot.z / MODEL_UNITS,
                radians(-bone.rotation.x),
                radians(-bone.rotation.y),
                radians(bone.rotation.z),
                bone.cubes.flatMap { cube -> bakeCube(cube, bone, geometry.textureWidth, geometry.textureHeight) }
            )
        }

        return BakedGeoModel(
            geometry.identifier,
            geometry.visibleBoundsWidth,
            geometry.visibleBoundsHeight,
            -geometry.visibleBoundsOffset.x,
            geometry.visibleBoundsOffset.y,
            geometry.visibleBoundsOffset.z,
            bones,
            indices
        )
    }

    private fun topologicalBones(source: List<BedrockBone>): List<BedrockBone> {
        val duplicates = source.groupingBy(BedrockBone::name)
            .eachCount()
            .filterValues { count -> count > 1 }
            .keys
        require(duplicates.isEmpty()) {
            "Duplicate GEO bone name(s): ${duplicates.sorted().joinToString()}"
        }

        val byName = source.associateBy(BedrockBone::name)
        source.forEach { bone ->
            val parent = bone.parent ?: return@forEach
            require(parent in byName) { "GEO bone '${bone.name}' references missing parent '$parent'" }
        }
        val result = arrayListOf<BedrockBone>()
        val visiting = hashSetOf<String>()
        val visited = hashSetOf<String>()

        fun visit(bone: BedrockBone) {
            if (bone.name in visited) return
            check(visiting.add(bone.name)) { "Circular GEO bone parent chain at ${bone.name}" }
            bone.parent?.let { parent -> byName[parent]?.let(::visit) }
            visiting.remove(bone.name)
            visited += bone.name
            result += bone
        }

        source.forEach(::visit)
        return result
    }

    private fun bakeCube(
        cube: BedrockCube,
        bone: BedrockBone,
        textureWidth: Int,
        textureHeight: Int
    ): List<BakedGeoQuad> {
        val inflate = (cube.inflate ?: bone.inflate) / MODEL_UNITS
        val mirror = cube.mirror ?: bone.mirror
        val minX = -(cube.origin.x + cube.size.x) / MODEL_UNITS - inflate
        val minY = cube.origin.y / MODEL_UNITS - inflate
        val minZ = cube.origin.z / MODEL_UNITS - inflate
        val maxX = minX + cube.size.x / MODEL_UNITS + inflate * 2F
        val maxY = minY + cube.size.y / MODEL_UNITS + inflate * 2F
        val maxZ = minZ + cube.size.z / MODEL_UNITS + inflate * 2F
        val vertices = arrayOf(
            Vector3f(minX, minY, minZ),
            Vector3f(minX, minY, maxZ),
            Vector3f(minX, maxY, minZ),
            Vector3f(minX, maxY, maxZ),
            Vector3f(maxX, maxY, minZ),
            Vector3f(maxX, maxY, maxZ),
            Vector3f(maxX, minY, minZ),
            Vector3f(maxX, minY, maxZ)
        )
        val pivot = Vector3f(-cube.pivot.x / MODEL_UNITS, cube.pivot.y / MODEL_UNITS, cube.pivot.z / MODEL_UNITS)
        val transform = Matrix4f()
            .translate(pivot)
            .rotateZ(radians(cube.rotation.z))
            .rotateY(radians(-cube.rotation.y))
            .rotateX(radians(-cube.rotation.x))
            .translate(-pivot.x, -pivot.y, -pivot.z)
        vertices.forEach { point ->
            val transformed = transform.transform(Vector4f(point, 1F))
            point.set(transformed.x, transformed.y, transformed.z)
        }
        val normalTransform = Matrix3f(transform).invert().transpose()

        return GeoFaceDirection.entries.mapNotNull { direction ->
            if (isZeroFace(cube, direction)) return@mapNotNull null
            val faceUv = faceUv(cube, direction) ?: return@mapNotNull null
            val vertexIndices = faceVertices(direction, mirror)
            val normal = normalTransform.transform(normal(direction, mirror)).normalize()
            val uv = rotateUvs(
                baseUvs(faceUv, textureWidth, textureHeight, mirror),
                faceUv.rotation
            )

            BakedGeoQuad(
                FloatArray(12) { index ->
                    val vertex = vertices[vertexIndices[index / 3]]
                    when (index % 3) {
                        0 -> vertex.x
                        1 -> vertex.y
                        else -> vertex.z
                    }
                },
                uv,
                normal.x,
                normal.y,
                normal.z
            )
        }
    }

    private fun faceUv(cube: BedrockCube, direction: GeoFaceDirection): BedrockUv.Face? = when (val uv = cube.uv) {
        is BedrockUv.PerFace -> uv.faces[direction]
        is BedrockUv.Box -> {
            val x = cube.size.x
            val y = cube.size.y
            val z = cube.size.z
            when (direction) {
                GeoFaceDirection.WEST -> BedrockUv.Face(uv.u + z + x, uv.v + z, z, y, 0, null)
                GeoFaceDirection.EAST -> BedrockUv.Face(uv.u, uv.v + z, z, y, 0, null)
                GeoFaceDirection.NORTH -> BedrockUv.Face(uv.u + z, uv.v + z, x, y, 0, null)
                GeoFaceDirection.SOUTH -> BedrockUv.Face(uv.u + z + x + z, uv.v + z, x, y, 0, null)
                GeoFaceDirection.UP -> BedrockUv.Face(uv.u + z, uv.v, x, z, 0, null)
                GeoFaceDirection.DOWN -> BedrockUv.Face(uv.u + z + x, uv.v + z, x, -z, 0, null)
            }
        }
    }

    private fun baseUvs(face: BedrockUv.Face, textureWidth: Int, textureHeight: Int, mirror: Boolean): FloatArray {
        var minU = face.u / textureWidth
        var maxU = (face.u + face.width) / textureWidth
        val minV = face.v / textureHeight
        val maxV = (face.v + face.height) / textureHeight
        if (!mirror) {
            val swap = minU
            minU = maxU
            maxU = swap
        }
        return floatArrayOf(minU, minV, maxU, minV, maxU, maxV, minU, maxV)
    }

    private fun rotateUvs(source: FloatArray, degrees: Int): FloatArray {
        val steps = ((degrees / 90) % 4 + 4) % 4
        if (steps == 0) return source
        return FloatArray(8) { index ->
            val targetVertex = index / 2
            val sourceVertex = (targetVertex - steps + 4) % 4
            source[sourceVertex * 2 + index % 2]
        }
    }

    private fun faceVertices(direction: GeoFaceDirection, mirror: Boolean): IntArray = when (direction) {
        GeoFaceDirection.WEST -> if (mirror) intArrayOf(4, 5, 7, 6) else intArrayOf(3, 2, 0, 1)
        GeoFaceDirection.EAST -> if (mirror) intArrayOf(3, 2, 0, 1) else intArrayOf(4, 5, 7, 6)
        GeoFaceDirection.NORTH -> intArrayOf(2, 4, 6, 0)
        GeoFaceDirection.SOUTH -> intArrayOf(5, 3, 1, 7)
        GeoFaceDirection.UP -> intArrayOf(3, 5, 4, 2)
        GeoFaceDirection.DOWN -> intArrayOf(0, 6, 7, 1)
    }

    private fun normal(direction: GeoFaceDirection, mirror: Boolean): Vector3f {
        val normal = when (direction) {
            GeoFaceDirection.WEST -> Vector3f(-1F, 0F, 0F)
            GeoFaceDirection.EAST -> Vector3f(1F, 0F, 0F)
            GeoFaceDirection.NORTH -> Vector3f(0F, 0F, -1F)
            GeoFaceDirection.SOUTH -> Vector3f(0F, 0F, 1F)
            GeoFaceDirection.UP -> Vector3f(0F, 1F, 0F)
            GeoFaceDirection.DOWN -> Vector3f(0F, -1F, 0F)
        }
        if (mirror) normal.x = -normal.x
        return normal
    }

    private fun isZeroFace(cube: BedrockCube, direction: GeoFaceDirection): Boolean = when {
        cube.size.x == 0F -> direction != GeoFaceDirection.WEST && direction != GeoFaceDirection.EAST
        cube.size.y == 0F -> direction != GeoFaceDirection.UP && direction != GeoFaceDirection.DOWN
        cube.size.z == 0F -> direction != GeoFaceDirection.NORTH && direction != GeoFaceDirection.SOUTH
        else -> false
    }

    private fun radians(degrees: Float): Float = degrees * (PI.toFloat() / 180F)
}
