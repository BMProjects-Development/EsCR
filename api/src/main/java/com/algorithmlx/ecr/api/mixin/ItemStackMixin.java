package com.algorithmlx.ecr.api.mixin;

import com.algorithmlx.ecr.api.item.ModifiableSizeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemInstance;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(ItemStack.class)
public abstract class ItemStackMixin implements ItemInstance {
    @Shadow
    public abstract Item getItem();

    @Override
    public int getMaxStackSize() {
        final var oldSize = ItemInstance.super.getMaxStackSize();

        if (this.getItem() instanceof ModifiableSizeItem sizi)
            return sizi.maxStackSize((ItemStack) (Object) this, oldSize);

        return oldSize;
    }
}
