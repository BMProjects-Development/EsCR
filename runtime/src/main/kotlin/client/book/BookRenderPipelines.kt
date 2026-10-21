package com.algorithmlx.ecr.client.book

import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.api.research.content.BookCategory
import com.algorithmlx.ecr.mixin.client.RenderPipelinesAccessor
import com.mojang.renderpearl.api.pipeline.RenderPipeline
import net.minecraft.resources.Identifier
import java.util.concurrent.ConcurrentHashMap

object BookRenderPipelines {
    private val custom = ConcurrentHashMap<SpacePipelineKey, RenderPipeline>()

    @JvmField
    val SPACE: RenderPipeline = create("pipeline/book_space".ecRL, "core/book_space".ecRL, "core/book_space".ecRL)
    @JvmField
    val THREAD: RenderPipeline = create("pipeline/book_thread".ecRL, "core/book_thread".ecRL, "core/book_thread".ecRL)

    fun forCategory(category: BookCategory?): RenderPipeline {
        val config = ResearchBookConfigValues.spaceShaderConfig()
        if (category == null && config.isDefault) return SPACE

        val shader = category?.shader
        val key = SpacePipelineKey(category?.id, config.key)
        return custom.computeIfAbsent(key) {
            create(
                location(category?.id, config),
                shader?.vertex ?: "core/book_space".ecRL,
                shader?.fragment ?: "core/book_space".ecRL,
                config
            )
        }
    }

    private fun location(category: Identifier?, config: ResearchBookSpaceShaderConfig): Identifier {
        if (category == null) return "pipeline/book_space/${config.key}".ecRL
        return Identifier.fromNamespaceAndPath(category.namespace, "pipeline/book/${category.path}/${config.key}")
    }

    private fun create(
        location: Identifier,
        vertex: Identifier,
        fragment: Identifier,
        config: ResearchBookSpaceShaderConfig? = null
    ): RenderPipeline {
        val builder = RenderPipeline.builder(RenderPipelinesAccessor.guiSnippet())
            .withLocation(location)
            .withVertexShader(vertex)
            .withFragmentShader(fragment)
        if (config != null) {
            builder.withShaderDefine("ECR_STAR_DENSITY", config.starDensity)
            builder.withShaderDefine("ECR_STAR_SIZE", config.starSize)
        }
        return RenderPipelinesAccessor.register(builder.build())
    }

    private data class SpacePipelineKey(
        val category: Identifier?,
        val config: String
    )
}
