package com.algorithmlx.ecr.mixin.client;

import com.algorithmlx.ecr.client.widget.mim.MIMWindowHost;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class MIMFloatingLayerMixin {
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(
        value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V"
    ))
    private void floatingWindows(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float deltaTicks, CallbackInfo ci) {
        if ((Object) this instanceof MIMWindowHost host) {
            graphics.nextStratum();
            host.extractFloatingWindows(graphics, mouseX, mouseY, deltaTicks);
        }
    }
}
