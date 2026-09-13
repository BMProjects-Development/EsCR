package com.algorithmlx.ecr.api.mixin.client;

import com.algorithmlx.ecr.api.geo.client.BedrockGeoGpuSubmit;
import com.algorithmlx.ecr.api.geo.client.BedrockGeoGpuSubmitCollector;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.feature.phase.SimpleFeatureRenderPhase;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SubmitNodeCollection.class)
public abstract class SubmitNodeCollectionMixin implements BedrockGeoGpuSubmitCollector {
    @Shadow
    @Final
    public SimpleFeatureRenderPhase solid;

    @Shadow
    @Final
    public SimpleFeatureRenderPhase translucentCustomGeometry;

    @SuppressWarnings("AddedMixinMembersNamePattern")
    @Unique
    @Override
    public void submitBedrockGeoGpu(BedrockGeoGpuSubmit submit) {
        if (submit.getRenderType().hasBlending()) this.translucentCustomGeometry.submit(submit);
        else this.solid.submit(submit);
    }
}
