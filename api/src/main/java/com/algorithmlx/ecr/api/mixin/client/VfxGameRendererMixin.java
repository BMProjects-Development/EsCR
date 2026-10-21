package com.algorithmlx.ecr.api.mixin.client;

import com.algorithmlx.ecr.api.vfx.VfxScreenEffects;
import com.algorithmlx.ecr.api.vfx.VfxGpuGeometry;
import com.mojang.blaze3d.pipeline.RenderTarget;
import java.util.List;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class VfxGameRendererMixin {
    @Shadow @Final private List<PostChain> appliedPostEffects;
    @Shadow @Final private RenderTarget mainRenderTarget;

    @Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"))
    private void materials(CallbackInfo callback) {
        VfxGpuGeometry.INSTANCE.render(mainRenderTarget);
    }

    @Inject(method = "preparePostEffects", at = @At("HEAD"))
    private void retainOwnedChains(List<Identifier> effects, CallbackInfo callback) {
        appliedPostEffects.removeIf(VfxScreenEffects.INSTANCE::owns);
    }

    @Inject(method = "preparePostEffects", at = @At("TAIL"))
    private void prepare(List<Identifier> effects, CallbackInfo callback) {
        appliedPostEffects.addAll(VfxScreenEffects.INSTANCE.prepare());
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void close(CallbackInfo callback) {
        VfxScreenEffects.INSTANCE.clear();
        VfxGpuGeometry.INSTANCE.clear();
    }
}
