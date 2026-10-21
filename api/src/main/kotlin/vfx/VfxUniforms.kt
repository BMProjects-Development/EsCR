package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.mixin.client.VfxPostChainAccessor
import com.algorithmlx.ecr.api.mixin.client.VfxPostPassAccessor
import kotlinx.serialization.json.JsonObject
import net.minecraft.client.renderer.PostChain
import net.minecraft.client.renderer.PostChainConfig
import net.minecraft.client.renderer.PostPass
import net.minecraft.client.renderer.texture.TextureManager
import net.minecraft.resources.Identifier

internal class VfxAuthoredUniform(val name: String, private val evaluate: (VfxNodeRuntime) -> FloatArray) {
    fun sample(node: VfxNodeRuntime): FloatArray = evaluate(node)
}

internal object VfxUniforms {
    private val identifier = Regex("[a-zA-Z_][a-zA-Z_0-9]*")

    fun validateName(name: String) {
        require(identifier.matches(name) && !name.startsWith("gl_")) { "Invalid GLSL field name: $name" }
    }

    fun parse(properties: JsonObject): List<VfxAuthoredUniform> {
        val names = hashSetOf<String>()
        return properties.array("uniforms").map { entry ->
            val field = entry.obj()
            val name = field.string("name")
            validateName(name)
            require(names.add(name)) { "Duplicate VFX uniform: $name" }
            val value = field.obj("value")
            val source = value["value"]
            when (val type = value.string("type", "float")) {
                "vector", "vec2" -> {
                    val vector = source.valueVector()
                    VfxAuthoredUniform(name) { node ->
                        node.sample("uniforms.$name", vector).let {
                            if (type == "vec2") floatArrayOf(it.x, it.y) else floatArrayOf(it.x, it.y, it.z)
                        }
                    }
                }
                "color" -> {
                    val color = source.color()
                    VfxAuthoredUniform(name) { node -> node.sample("uniforms.$name", color) }
                }
                "float" -> {
                    val scalar = source.value()
                    VfxAuthoredUniform(name) { node -> floatArrayOf(node.sample("uniforms.$name", scalar)) }
                }
                else -> error("Unsupported VFX uniform type: $type")
            }
        }
    }
}

internal data class VfxTextureBinding(val name: String, val texture: Identifier)

internal object VfxSamplers {
    fun parse(properties: JsonObject, reserved: Set<String> = emptySet()): List<VfxTextureBinding> {
        val names = reserved.toMutableSet()
        return properties.array("samplers").map { item ->
            val sampler = item.obj()
            val name = sampler.string("name")
            VfxUniforms.validateName(name)
            require(names.add(name)) { "Duplicate or reserved VFX sampler: $name" }
            VfxTextureBinding(name, Identifier.parse(sampler.string("texture")))
        }
    }

    fun resolveTextures(config: PostChainConfig, properties: JsonObject): Map<String, Identifier> {
        val bindings = parse(properties).associate { it.name to it.texture }
        if (bindings.isEmpty()) return bindings
        val matched = hashSetOf<String>()
        config.passes().forEach { pass ->
            pass.inputs().forEach { input ->
                val name = input.samplerName() + "Sampler"
                if (name in bindings) {
                    require(input is PostChainConfig.TextureInput) { "Cannot override render target sampler: $name" }
                    matched += name
                }
            }
        }
        require(matched == bindings.keys) { "Unknown post-chain samplers: ${bindings.keys - matched}" }
        return bindings
    }

    fun bind(chain: PostChain, textures: TextureManager, bindings: Map<String, Identifier>) {
        (chain as VfxPostChainAccessor).passes.forEach { pass ->
            (pass as VfxPostPassAccessor).inputs.replaceAll { input ->
                val texture = bindings[input.samplerName() + "Sampler"]
                if (texture == null || input !is PostPass.TextureInput)
                    input
                else
                    PostPass.TextureInput(input.samplerName(), textures.getTexture(texture), input.width(), input.height(), input.bilinear())
            }
        }
    }
}
