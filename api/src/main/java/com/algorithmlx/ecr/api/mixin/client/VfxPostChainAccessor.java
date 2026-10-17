package com.algorithmlx.ecr.api.mixin.client;

import java.util.List;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PostChain.class)
public interface VfxPostChainAccessor {
    @Accessor("passes")
    List<PostPass> getPasses();

    @Accessor("projection")
    Projection getProjection();

    @Accessor("projectionMatrixBuffer")
    ProjectionMatrixBuffer getProjectionMatrixBuffer();
}
