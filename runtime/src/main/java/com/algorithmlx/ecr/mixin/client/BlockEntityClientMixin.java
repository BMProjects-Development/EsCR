package com.algorithmlx.ecr.mixin.client;

import com.algorithmlx.ecr.client.renderer.MRULinkClientTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
public abstract class BlockEntityClientMixin {

    @Shadow
    public abstract @Nullable Level getLevel();

    @Inject(method = "setLevel", at = @At("TAIL"))
    private void setLevel(Level level, CallbackInfo ci) {
        if (!level.isClientSide()) return;

        MRULinkClientTracker.register((BlockEntity) (Object) this);
    }

    @Inject(method = "setRemoved", at = @At("TAIL"))
    private void setRemoved(CallbackInfo ci) {
        MRULinkClientTracker.unregister((BlockEntity) (Object) this);
    }

    @Inject(method = "clearRemoved", at = @At("TAIL"))
    private void clearRemoved(CallbackInfo ci) {
        var level = this.getLevel();

        if (level == null || !level.isClientSide()) return;

        MRULinkClientTracker.register((BlockEntity) (Object) this);
    }
}
