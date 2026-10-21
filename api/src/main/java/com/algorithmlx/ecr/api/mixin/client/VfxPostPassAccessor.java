package com.algorithmlx.ecr.api.mixin.client;

import com.mojang.renderpearl.api.buffers.GpuBuffer;
import java.util.Map;
import java.util.List;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PostPass.class)
public interface VfxPostPassAccessor {
    @Accessor("customUniforms")
    Map<String, GpuBuffer> getCustomUniforms();

    @Accessor("inputs")
    List<PostPass.Input> getInputs();
}
