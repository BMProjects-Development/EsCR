package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.geo.GeoLightMode
import com.algorithmlx.ecr.api.geo.GeoModel
import com.algorithmlx.ecr.api.geo.GeoRenderType
import com.algorithmlx.ecr.api.geo.client.BedrockGeoRenderEngine
import com.algorithmlx.ecr.api.geo.client.BedrockGeoRenderData
import com.algorithmlx.ecr.api.molang.runtime.MolangContext
import com.algorithmlx.ecr.api.particle.light.LevelLightProvider
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import kotlinx.serialization.json.JsonObject
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.state.level.LevelRenderState
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.Identifier
import net.minecraft.util.LightCoordsUtil
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class VfxVertex(val position: Vector3f, val u: Float, val v: Float, val color: Int? = null)

internal data class VfxQuad(
    val vertices: List<VfxVertex>,
    val color: Int,
    val light: Int,
    val distance: Float
) {
    fun submit(pose: PoseStack.Pose, consumer: VertexConsumer) {
        vertices.forEach { vertex ->
            consumer.addVertex(pose, vertex.position).setUv(vertex.u, vertex.v).setColor(vertex.color ?: color).setLight(light)
        }
    }
}

internal data class VfxPass(val material: JsonObject, val state: VfxMaterialSnapshot?)

internal class VfxSurfaceConfig(spec: VfxNode) {
    val material = spec.properties.obj("material")
    val state = VfxMaterialState(material)
    val appearance = spec.properties.obj("particle")
    val size = appearance["size"].valueVector(0.25f)
    val rotation = appearance["rotation"].valueVector()
    val color = appearance["color"].color()
    val tint = spec.properties["tint"].color()
    val width = spec.properties["width"].value(0.1f)
    val noise = spec.properties["noise"].value()
    val end = spec.properties["end"]?.valueVector() ?: VfxVector(VfxValue.Constant(0f), VfxValue.Constant(2f), VfxValue.Constant(0f))
}

internal class VfxRenderer(private val instance: VfxInstance) {
    private val light = LevelLightProvider(requireNotNull(instance.level))
    private val passes = linkedMapOf<VfxPass, MutableList<VfxQuad>>()
    private val camera = Vector3f()
    private val configs = hashMapOf<VfxNode, VfxSurfaceConfig>()
    private val models = hashMapOf<VfxNode, GeoModel>()
    private val frameModels = hashMapOf<VfxNode, BedrockGeoRenderData?>()
    private lateinit var poseStack: PoseStack
    private lateinit var collector: SubmitNodeCollector
    private var partialTick = 0f
    private var quadCount = 0
    private var currentState: VfxMaterialSnapshot? = null
    private val ribbons = VfxRibbonRenderer(camera, ::config) { node, material, world, vertices ->
        currentState = config(node).state.sample(node)
        quad(material, world, -1, if (material.string("lighting") == "WORLD") light.query(world) else LightCoordsUtil.FULL_BRIGHT, vertices)
    }

    private fun config(node: VfxNodeRuntime) = configs.getOrPut(node.spec) { VfxSurfaceConfig(node.spec) }

    fun submit(poseStack: PoseStack, collector: SubmitNodeCollector, state: LevelRenderState, partialTick: Float) {
        this.poseStack = poseStack
        this.collector = collector
        this.partialTick = partialTick.coerceIn(0f, 1f)
        passes.clear()
        frameModels.clear()
        quadCount = 0
        camera.set(state.cameraRenderState.pos.x.toFloat(), state.cameraRenderState.pos.y.toFloat(), state.cameraRenderState.pos.z.toFloat())
        instance.effect.nodes.forEach { root -> instance.node(root.id)?.let { collect(it, partialTick.coerceIn(0f, 1f), Quaternionf(state.cameraRenderState.orientation)) } }
        poseStack.pushPose()
        poseStack.translate(-state.cameraRenderState.pos.x, -state.cameraRenderState.pos.y, -state.cameraRenderState.pos.z)
        try {
            passes.forEach { (pass, quads) ->
                if (quads.isEmpty()) return@forEach
                val ordered = if (pass.material.string("blend", "BLEND") in setOf("BLEND", "TRANSLUCENT")) quads.sortedByDescending { it.distance } else quads
                val type = VfxMaterialRenderTypes.particle(pass.material)
                val geometry: (PoseStack.Pose, VertexConsumer) -> Unit = { pose, consumer ->
                    ordered.forEach { it.submit(pose, consumer) }
                }
                if (pass.state != null) VfxGpuGeometry.submit(pass.material, null, pass.state, poseStack.last(), geometry)
                else collector.submitCustomGeometry(poseStack, type, geometry)
            }
        } finally {
            poseStack.popPose()
        }
    }

    private fun collect(node: VfxNodeRuntime, partial: Float, cameraRotation: Quaternionf) {
        if (!node.active) return
        when (node.spec.type) {
            "plane", "cube", "sphere", "cylinder" -> {
                val owner = node.particleOwner?.emitter
                if (owner == null) drawSurface(node, null, partial, cameraRotation)
                else owner.particles.forEach { drawSurface(node, it, partial, cameraRotation) }
            }
            "model" -> {
                val owner = node.particleOwner?.emitter
                if (owner == null) drawModel(node, null)
                else owner.particles.forEach { drawModel(node, it) }
            }
            "beam" -> {
                val owner = node.particleOwner?.emitter
                if (owner == null) ribbons.beam(node, null, partial)
                else owner.particles.forEach { ribbons.beam(node, it, partial) }
            }
            "trail" -> {
                val owner = node.particleOwner?.emitter
                if (owner != null) owner.particles.forEach { ribbons.trail(node, it) }
                else ribbons.trail(node, null)
            }
        }
        node.children.forEach { collect(it, partial, cameraRotation) }
    }

    private fun drawModel(node: VfxNodeRuntime, particle: VfxParticle?) {
        val spec = node.spec.properties
        val geometry = spec.string("model", spec.string("geometry"))
        val texture = Identifier.tryParse(spec.string("texture", spec.obj("material").string("texture"))) ?: return
        if (geometry.isBlank()) return
        val model = models.getOrPut(node.spec) {
            val type = when (spec.obj("material").string("blend", "CUTOUT")) {
                "ADDITIVE" -> GeoRenderType.ADDITIVE
                "BLEND", "TRANSLUCENT" -> GeoRenderType.TRANSLUCENT
                "OPAQUE", "SOLID" -> GeoRenderType.SOLID
                else -> GeoRenderType.CUTOUT
            }
            val lightMode = if (spec.obj("material").string("lighting") == "WORLD") GeoLightMode.WORLD else GeoLightMode.FULL_BRIGHT
            val identifier = if (geometry.startsWith("geometry.")) null else Identifier.tryParse(geometry)
            if (identifier != null) GeoModel(identifier, texture, type, lightMode = lightMode)
            else GeoModel(geometry, texture, type, lightMode = lightMode)
        }
        val context = instance.context
        val frame = VfxRenderFrame.prepare(node, particle, partialTick)
        val world = frame.getTranslation(Vector3f())
        if (world.distanceSquared(camera) > 256f * 256f) return
        context.cameraDistance = world.distance(camera)
        val data = if (frameModels.containsKey(node.spec)) frameModels[node.spec]
            else BedrockGeoRenderEngine.extract(model, emptyList(), MolangContext.EMPTY, instance.time.toDouble()).also { frameModels[node.spec] = it }
        if (data == null) return
        val surface = config(node)
        val sizeValue = if (particle == null) Vector3f(1f) else node.sample("appearance.size", surface.size)
        val size = if (surface.appearance.bool("uniformSize", true)) Vector3f(sizeValue.x) else sizeValue
        if (particle != null) frame.rotateXYZ(node.sample("appearance.rotation", surface.rotation).mul(PI.toFloat() / 180f))
        poseStack.pushPose()
        poseStack.translate(-camera.x.toDouble(), -camera.y.toDouble(), -camera.z.toDouble())
        poseStack.mulPose(frame.scale(size))
        val type = VfxMaterialRenderTypes.model(surface.material, model.texture)
        val color = multiplyColor(node.sample("appearance.color", surface.color), node.sample("tint", surface.tint))
        val packedLight = if (model.lightMode == GeoLightMode.WORLD) light.query(world) else LightCoordsUtil.FULL_BRIGHT
        val state = surface.state.sample(node)
        if (state == null) BedrockGeoRenderEngine.submit(data, poseStack, collector, packedLight, OverlayTexture.NO_OVERLAY, type, color)
        else {
            poseStack.scale(data.scale, data.scale, data.scale)
            VfxGpuGeometry.submit(surface.material, model.texture, state, poseStack.last()) { pose, consumer ->
                BedrockGeoRenderEngine.emitVertices(data, pose, consumer, packedLight, OverlayTexture.NO_OVERLAY, color)
            }
        }
        poseStack.popPose()
    }

    private fun drawSurface(node: VfxNodeRuntime, particle: VfxParticle?, partial: Float, cameraRotation: Quaternionf) {
        if (quadCount >= 50000) return
        val spec = node.spec.properties
        val config = config(node)
        val material = config.material
        val context = instance.context
        val matrix = VfxRenderFrame.prepare(node, particle, partial)
        val world = matrix.getTranslation(Vector3f())
        if (world.distanceSquared(camera) > 256f * 256f) return
        context.cameraDistance = world.distance(camera)
        currentState = config.state.sample(node)
        val sizeValue = if (particle == null) Vector3f(1f) else node.sample("appearance.size", config.size)
        val size = if (config.appearance.bool("uniformSize", true)) Vector3f(sizeValue.x) else sizeValue
        val color = multiplyColor(node.sample("appearance.color", config.color), node.sample("tint", config.tint))
        val lightValue = if (material.string("lighting") == "WORLD") light.query(world) else LightCoordsUtil.FULL_BRIGHT
        if (node.spec.type == "plane") VfxRenderFrame.face(matrix, node, particle, camera, cameraRotation)
        if (particle != null) matrix.rotateXYZ(node.sample("appearance.rotation", config.rotation).mul(PI.toFloat() / 180f))
        if (spec.bool("alignToVelocity") && particle != null && particle.velocity.lengthSquared() > 0.000001f) {
            val direction = Vector3f(particle.velocity)
            if (particle.local) node.particleOwner?.matrix?.transformDirection(direction)
            matrix.rotate(Quaternionf().rotationTo(Vector3f(0f, 1f, 0f), direction.normalize()))
        }
        val uv = material.obj("uv")
        var u0 = uv.number("u0")
        var v0 = uv.number("v0")
        var u1 = uv.number("u1", 1f)
        var v1 = uv.number("v1", 1f)
        val animation = node.particleOwner?.spec?.properties?.array("modules")
            ?.map { it.obj() }
            ?.firstOrNull { it.bool("enabled", true) && it.string("type").substringAfterLast('/') == "uv_animation" }
        if (animation != null && particle != null) {
            val columns = animation.number("columns", 4f).toInt().coerceIn(1, 64)
            val rows = animation.number("rows", 4f).toInt().coerceIn(1, 64)
            val frames = animation.number("frames").toInt().takeIf { it > 0 }?.coerceAtMost(columns * rows) ?: columns * rows
            val frame = when (animation.string("mode", "OVER_LIFETIME")) {
                "FPS" -> (particle.age * animation.number("fps", 12f)).toInt()
                "RANDOM_FRAME" -> (particle.random * frames).toInt()
                else -> (particle.progress * animation.number("cycles", 1f) * frames).toInt()
            }.mod(frames)
            val frameU = (u1 - u0) / columns
            val frameV = (v1 - v0) / rows
            val baseU = u0 + frame % columns * frameU
            val baseV = v0 + frame / columns * frameV
            u0 = baseU
            v0 = baseV
            u1 = baseU + frameU
            v1 = baseV + frameV
        }
        when (node.spec.type) {
            "plane" -> quad(
                material, world, color, lightValue,
                listOf(
                    vertex(matrix, -size.x * 0.5f, -size.y * 0.5f, 0f, u0, v1),
                    vertex(matrix, -size.x * 0.5f, size.y * 0.5f, 0f, u0, v0),
                    vertex(matrix, size.x * 0.5f, size.y * 0.5f, 0f, u1, v0),
                    vertex(matrix, size.x * 0.5f, -size.y * 0.5f, 0f, u1, v1)
                )
            )
            "cube" -> drawCube(material, matrix, world, size, color, lightValue, u0, v0, u1, v1)
            "sphere", "cylinder" -> {
                val segments = spec.number("segments", 24f).toInt().coerceIn(4, 64)
                drawRound(node.spec.type, material, matrix, world, size, color, lightValue, segments, u0, v0, u1, v1)
                if (node.spec.type == "cylinder" && spec.bool("caps", true)) {
                    for (side in listOf(-1f, 1f)) for (slice in 0 until segments) {
                        val a = side * 2f * PI.toFloat() * slice / segments
                        val b = side * 2f * PI.toFloat() * (slice + 1) / segments
                        val y = side * size.y * 0.5f
                        quad(material, world, color, lightValue, listOf(
                            vertex(matrix, 0f, y, 0f, (u0 + u1) * 0.5f, (v0 + v1) * 0.5f),
                            vertex(matrix, cos(a) * size.x * 0.5f, y, sin(a) * size.z * 0.5f, u0 + (u1 - u0) * (cos(a) + 1f) * 0.5f, v0 + (v1 - v0) * (sin(a) + 1f) * 0.5f),
                            vertex(matrix, cos(b) * size.x * 0.5f, y, sin(b) * size.z * 0.5f, u0 + (u1 - u0) * (cos(b) + 1f) * 0.5f, v0 + (v1 - v0) * (sin(b) + 1f) * 0.5f),
                            vertex(matrix, 0f, y, 0f, (u0 + u1) * 0.5f, (v0 + v1) * 0.5f)
                        ))
                    }
                }
            }
        }
    }

    private fun drawCube(material: JsonObject, matrix: Matrix4f, world: Vector3f, size: Vector3f, color: Int, lightValue: Int, u0: Float, v0: Float, u1: Float, v1: Float) {
        val x = size.x * 0.5f
        val y = size.y * 0.5f
        val z = size.z * 0.5f
        val faces = listOf(
            floatArrayOf(-x, -y, z, -x, y, z, x, y, z, x, -y, z),
            floatArrayOf(x, -y, -z, x, y, -z, -x, y, -z, -x, -y, -z),
            floatArrayOf(-x, y, z, -x, y, -z, x, y, -z, x, y, z),
            floatArrayOf(-x, -y, -z, -x, -y, z, x, -y, z, x, -y, -z),
            floatArrayOf(x, -y, z, x, y, z, x, y, -z, x, -y, -z),
            floatArrayOf(-x, -y, -z, -x, y, -z, -x, y, z, -x, -y, z)
        )
        faces.forEach { face ->
            quad(material, world, color, lightValue, List(4) { index ->
                vertex(matrix, face[index * 3], face[index * 3 + 1], face[index * 3 + 2], if (index == 0 || index == 1) u0 else u1, if (index == 0 || index == 3) v1 else v0)
            })
        }
    }

    private fun drawRound(type: String, material: JsonObject, matrix: Matrix4f, world: Vector3f, size: Vector3f, color: Int, lightValue: Int, segments: Int, u0: Float, v0: Float, u1: Float, v1: Float) {
        val rings = if (type == "sphere") segments / 2 else 1
        for (ring in 0 until rings) for (slice in 0 until segments) {
            if (quadCount >= 50000) return
            fun point(r: Int, s: Int): Vector3f {
                val latitude = if (type == "sphere") -PI.toFloat() * 0.5f + PI.toFloat() * r / rings else if (r == 0) -PI.toFloat() * 0.5f else PI.toFloat() * 0.5f
                val longitude = 2f * PI.toFloat() * s / segments
                val radius = if (type == "sphere") cos(latitude) else 1f
                return Vector3f(radius * cos(longitude) * size.x * 0.5f, sin(latitude) * size.y * 0.5f, radius * sin(longitude) * size.z * 0.5f)
            }
            val points = listOf(point(ring, slice), point(ring + 1, slice), point(ring + 1, slice + 1), point(ring, slice + 1))
            quad(material, world, color, lightValue, points.mapIndexed { index, point ->
                val u = u0 + (u1 - u0) * (slice + if (index >= 2) 1 else 0) / segments
                val v = v1 - (v1 - v0) * (ring + if (index == 1 || index == 2) 1 else 0) / rings
                vertex(matrix, point.x, point.y, point.z, u, v)
            })
        }
    }

    private fun quad(material: JsonObject, world: Vector3f, color: Int, lightValue: Int, vertices: List<VfxVertex>) {
        if (quadCount >= 50000 || world.distanceSquared(camera) > 256f * 256f) return
        passes.getOrPut(VfxPass(material, currentState), ::ArrayList).add(VfxQuad(vertices, color, lightValue, world.distanceSquared(camera)))
        quadCount++
    }

    private fun vertex(matrix: Matrix4f, x: Float, y: Float, z: Float, u: Float, v: Float) = VfxVertex(matrix.transformPosition(Vector3f(x, y, z)), u, v)

    private fun multiplyColor(a: FloatArray, b: FloatArray) = pack(FloatArray(4) { a[it] * b[it] })

    private fun pack(channels: FloatArray): Int {
        fun channel(index: Int) = (channels[index].coerceIn(0f, 1f) * 255f).toInt()
        return channel(3) shl 24 or (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
    }
}
