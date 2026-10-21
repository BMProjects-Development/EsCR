package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.particle.light.LevelLightProvider
import com.algorithmlx.ecr.api.vfx.gltf.VfxGltfAssets
import com.algorithmlx.ecr.api.vfx.gltf.VfxGltfMesh
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology
import kotlinx.serialization.json.JsonObject
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.util.LightCoordsUtil
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.PI

internal class VfxGltfRenderer(private val light: LevelLightProvider) {
    private val materials = hashMapOf<Pair<VfxNode, VfxGltfMesh>, Pair<JsonObject, VfxMaterialState>>()

    fun draw(node: VfxNodeRuntime, particle: VfxParticle?, partial: Float, surface: VfxSurfaceConfig, camera: Vector3f, stack: PoseStack, collector: SubmitNodeCollector) {
        val model = VfxGltfAssets.get(node.spec.properties.string("model")) ?: return
        val frame = VfxRenderFrame.prepare(node, particle, partial)
        val position = frame.getTranslation(Vector3f())
        if (position.distanceSquared(camera) > 256f * 256f) return
        node.instance.context.cameraDistance = position.distance(camera)
        val size = if (particle == null) Vector3f(1f) else node.sample("appearance.size", surface.size)
        if (surface.appearance.bool("uniformSize", true)) size.set(size.x)
        if (particle != null) frame.rotateXYZ(node.sample("appearance.rotation", surface.rotation).mul(PI.toFloat() / 180f))
        if (node.spec.properties.bool("alignToVelocity") && particle != null && particle.velocity.lengthSquared() > 0.000001f) {
            val direction = Vector3f(particle.velocity)
            if (particle.local) node.particleOwner?.matrix?.transformDirection(direction)
            frame.rotate(Quaternionf().rotationTo(Vector3f(0f, 1f, 0f), direction.normalize()))
        }
        val appearance = node.sample("appearance.color", surface.color)
        val tint = node.sample("tint", surface.tint)
        stack.pushPose()
        stack.translate(-camera.x.toDouble(), -camera.y.toDouble(), -camera.z.toDouble())
        stack.mulPose(frame.scale(size))
        try {
            model.meshes.forEach { mesh ->
                val (material, state) = materials.getOrPut(node.spec to mesh) {
                    val merged = JsonObject(mesh.material + surface.material)
                    merged to VfxMaterialState(merged)
                }
                val texture = VfxGltfAssets.texture(mesh.texture)
                val lightValue = if (material.string("lighting") == "WORLD") light.query(position) else LightCoordsUtil.FULL_BRIGHT
                val draw: (PoseStack.Pose, VertexConsumer) -> Unit = { pose, consumer ->
                    mesh.vertices.forEach { vertex ->
                        consumer.addVertex(pose, vertex.position).setColor(VfxColors.multiply(appearance, tint, vertex.color))
                            .setUv(vertex.uv.x, vertex.uv.y).setOverlay(OverlayTexture.NO_OVERLAY).setLight(lightValue).setNormal(pose, vertex.normal)
                    }
                }
                val sampled = state.sample(node)
                if (sampled == null)
                    collector.submitCustomGeometry(stack, VfxMaterialRenderTypes.model(material, texture, topology = PrimitiveTopology.TRIANGLES), draw)
                else
                    VfxGpuGeometry.submitTriangles(material, texture, sampled, stack.last(), draw)
            }
        } finally { stack.popPose() }
    }
}
