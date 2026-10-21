package com.algorithmlx.ecr.mixin.client;

import com.algorithmlx.ecr.client.utils.BoundGemSlotTooltips;
import java.util.List;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class BoundGemSlotTooltipMixin {
    @Shadow @Final protected AbstractContainerMenu menu;
    @Shadow protected Slot hoveredSlot;

    @Inject(method = "getTooltipFromContainerItem", at = @At("HEAD"), cancellable = true)
    private void contextualBoundGemTooltip(ItemStack stack, CallbackInfoReturnable<List<Component>> callback) {
        List<Component> lines = BoundGemSlotTooltips.lines(menu, hoveredSlot, stack);
        if (lines != null) callback.setReturnValue(lines);
    }
}
