package com.algorithmlx.ecr.api.mixin.client;

import com.algorithmlx.ecr.api.geo.client.BedrockGeoGpuSubmit;
import com.algorithmlx.ecr.api.geo.client.BedrockGeoGpuSubmitCollector;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import org.jspecify.annotations.NonNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@Mixin(SubmitNodeStorage.class)
public abstract class SubmitNodeStorageMixin implements BedrockGeoGpuSubmitCollector {
    @Shadow
    public abstract SubmitNodeCollection order(int order);

    @SuppressWarnings("AddedMixinMembersNamePattern")
    @Unique
    @Override
    public void submitBedrockGeoGpu(@NonNull BedrockGeoGpuSubmit submit) {
        ((BedrockGeoGpuSubmitCollector) this.order(0)).submitBedrockGeoGpu(submit);
    }
}
