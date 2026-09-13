package com.algorithmlx.ecr.api.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("ENTITY_SNIPPET")
    static RenderPipeline.Snippet entitySnippet() {
        throw new AssertionError();
    }

    @Accessor("ENTITY_EMISSIVE_SNIPPET")
    static RenderPipeline.Snippet entityEmissiveSnippet() {
        throw new AssertionError();
    }

    @Accessor("PARTICLE_SNIPPET")
    static RenderPipeline.Snippet particleSnippet() {
        throw new AssertionError();
    }

    @Invoker("register")
    static RenderPipeline register(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
