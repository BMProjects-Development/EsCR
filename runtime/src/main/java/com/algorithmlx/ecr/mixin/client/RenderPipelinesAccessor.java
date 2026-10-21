package com.algorithmlx.ecr.mixin.client;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.RenderPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RenderPipelines.class)
public interface RenderPipelinesAccessor {
    @Accessor("GUI_SNIPPET")
    static RenderPipeline.Snippet guiSnippet() {
        throw new AssertionError();
    }

    @Accessor("DEBUG_FILLED_SNIPPET")
    static RenderPipeline.Snippet debugFilledSnippet() {
        throw new AssertionError();
    }

    @Invoker("register")
    static RenderPipeline register(RenderPipeline pipeline) {
        throw new AssertionError();
    }
}
