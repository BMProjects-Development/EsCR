package com.algorithmlx.ecr.api.mixin.client;

import com.algorithmlx.ecr.api.vfx.VfxScenes;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class VfxCameraMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void shake(CameraRenderState state, DeltaTracker tracker, CallbackInfo callback) {
        VfxScenes.INSTANCE.shakeCamera(state);
    }
}
