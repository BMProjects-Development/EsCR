package com.algorithmlx.ecr.api.mixin;

import com.algorithmlx.ecr.api.recipe.dsl.RecipeCompiler;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// w in chat
@Mixin(RecipeManager.class)
public abstract class RecipeManagerMixin {
    @Shadow
    @Final
    @Mutable
    private RecipeMap recipes;

    @Inject(
            method = "<init>",
            at = @At("TAIL")
    )
    private void init(HolderLookup.Provider registries, CallbackInfo ci) {
        this.recipes = RecipeCompiler.merge(this.recipes, registries);
    }
}
