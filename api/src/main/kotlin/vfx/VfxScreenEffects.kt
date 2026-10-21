package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.LOGGER
import com.algorithmlx.ecr.api.mixin.client.VfxPostChainAccessor
import com.google.gson.JsonParser
import com.mojang.serialization.JsonOps
import kotlinx.serialization.json.Json
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.LevelTargetBundle
import net.minecraft.client.renderer.PostChain
import net.minecraft.client.renderer.PostChainConfig
import net.minecraft.resources.Identifier
import org.joml.Vector3f
import org.joml.Vector4f

object VfxScreenEffects {
    private class Entry(val chain: PostChain, val uniforms: VfxPostUniforms)

    private val entries = linkedMapOf<VfxNodeRuntime, Entry?>()

    fun owns(chain: PostChain): Boolean = entries.values.any { it?.chain === chain }

    fun prepare(): List<PostChain> {
        val prepared = arrayListOf<PostChain>()
        val nodes = VfxScenes.instances().filterNot { it.stopping }.flatMap { instance ->
            instance.effect.nodes.flatMap { instance.node(it.id)?.walk().orEmpty() }
        }.filter { it.active && (it.spec.type == "post_effect" || it.spec.type == "sky") }
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val (node, entry) = iterator.next()
            if (node !in nodes) {
                entry?.chain?.close()
                iterator.remove()
            }
        }
        nodes.forEach { node ->
            if (!entries.containsKey(node)) entries[node] = load(node)
            val entry = entries[node] ?: return@forEach
            val instance = node.instance
            val context = instance.context
            context.useEffect(instance.time, instance.effect.timeline.duration, instance.effect.timeline.loop)
            val camera = Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState
            val origin = node.matrix.transformPosition(Vector3f())
            val world = instance.position.add(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble())
            context.position = origin
            context.cameraDistance = camera.pos.distanceTo(world).toFloat()
            val center = Vector4f(
                (world.x - camera.pos.x).toFloat(), (world.y - camera.pos.y).toFloat(), (world.z - camera.pos.z).toFloat(), 1f
            ).mul(camera.viewRotationMatrix).mul(camera.projectionMatrix)
            val projected = if (center.w > 0f) floatArrayOf(0.5f + center.x / center.w * 0.5f, 0.5f + center.y / center.w * 0.5f)
            else floatArrayOf(-10f, -10f)
            entry.uniforms.upload(entry.chain, node, projected)
            prepared += entry.chain
        }
        return prepared
    }

    private fun load(node: VfxNodeRuntime): Entry? = runCatching {
        val client = Minecraft.getInstance()
        val fallback = if (node.spec.type == "sky") "escr:vfx/sky/shockwave" else "escr:vfx/post/grayscale"
        val shader = Identifier.parse(node.spec.properties.string("shader", fallback))
        val resource = Identifier.fromNamespaceAndPath(shader.namespace, "post_effect/${shader.path}.json")
        val text = client.resourceManager.getResourceOrThrow(resource).openAsReader().use { it.readText() }
        val config = PostChainConfig.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(text)).getOrThrow()
        val bindings = VfxSamplers.resolveTextures(config, node.spec.properties)
        val template = requireNotNull(client.shaderManager.getPostChain(shader, LevelTargetBundle.MAIN_TARGETS)) as VfxPostChainAccessor
        val chain = PostChain.load(config, client.textureManager, LevelTargetBundle.MAIN_TARGETS, shader, template.projection, template.projectionMatrixBuffer)
        VfxSamplers.bind(chain, client.textureManager, bindings)
        Entry(chain, VfxPostUniforms(Json.parseToJsonElement(text).obj(), config, node.spec.properties))
    }.onFailure { LOGGER.error("Unable to initialize VFX screen node {}", node.spec.id, it) }.getOrNull()

    fun clear() {
        entries.values.forEach { it?.chain?.close() }
        entries.clear()
    }
}
