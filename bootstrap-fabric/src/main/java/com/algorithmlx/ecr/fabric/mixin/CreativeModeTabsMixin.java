package com.algorithmlx.ecr.fabric.mixin;

import com.algorithmlx.ecr.fabric.init.registry.creative.FabricCreativeTabOrdering;
import net.minecraft.world.item.CreativeModeTabs;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CreativeModeTabs.class, priority = 900)
public class CreativeModeTabsMixin {
    @Inject(method = "buildAllTabContents", at = @At("RETURN"))
    private static void applyOrdering(CallbackInfo ci) {
        FabricCreativeTabOrdering.reorder();
    }
}
