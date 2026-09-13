package com.algorithmlx.ecr.api.mixin.client;

import com.algorithmlx.ecr.api.geo.client.BedrockGeoGpuFeatureRenderer;
import com.algorithmlx.ecr.api.geo.client.BedrockGeoGpuPipelines;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.feature.FeatureRendererMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FeatureRenderDispatcher.class)
public abstract class FeatureRenderDispatcherMixin {
    @Shadow
    @Final
    private FeatureRendererMap featureRenderers;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(CallbackInfo callbackInfo) {
        BedrockGeoGpuPipelines.ensureInitialized();
        this.featureRenderers.put(BedrockGeoGpuFeatureRenderer.TYPE, new BedrockGeoGpuFeatureRenderer());
    }
}
