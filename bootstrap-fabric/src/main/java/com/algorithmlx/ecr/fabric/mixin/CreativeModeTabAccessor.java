package com.algorithmlx.ecr.fabric.mixin;

import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CreativeModeTab.class)
public interface CreativeModeTabAccessor {
    @Accessor("row")
    @Mutable
    @Final
    void setRow(CreativeModeTab.Row row);

    @Accessor("column")
    @Mutable
    @Final
    void setColumn(int column);
}
