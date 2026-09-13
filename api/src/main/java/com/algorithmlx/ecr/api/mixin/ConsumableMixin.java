package com.algorithmlx.ecr.api.mixin;

import com.algorithmlx.ecr.api.item.NoConsumeBreakItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Consumable.class)
public class ConsumableMixin {
    @Inject(method = "canConsume", at = @At("HEAD"), cancellable = true)
    private void canConsume(LivingEntity user, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof NoConsumeBreakItem) cir.setReturnValue(true); // always true for result invoke.
    }

    @Inject(method = "onConsume", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;consume(ILnet/minecraft/world/entity/LivingEntity;)V", shift = At.Shift.BEFORE), cancellable = true)
    private void onConsume(Level level, LivingEntity user, ItemStack stack, CallbackInfoReturnable<ItemStack> cir) {
        if (stack.getItem() instanceof NoConsumeBreakItem item) {
            final var result = item.onConsume(level, user, stack);
            if (result != ItemStack.EMPTY) cir.setReturnValue(result);
        }
    }
}
