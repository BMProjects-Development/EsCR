package com.algorithmlx.ecr.mixin;

import com.algorithmlx.ecr.api.recipe.CachedRecipe;
import com.algorithmlx.ecr.api.utils.UtilitiesKt;
import com.algorithmlx.ecr.init.events.ECEvents;
import com.algorithmlx.ecr.registry.RecipeTypeRegistry;
import com.algorithmlx.ecr.common.recipe.StructureRecipe;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin extends Entity {
    @Shadow
    public abstract ItemStack getItem();

    @Unique
    private final int[] ecr$ticker = UtilitiesKt.makeIntArray(0);

    @Unique
    private CachedRecipe<SingleRecipeInput, StructureRecipe> ecr$recipe;

    public ItemEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    public void tick(CallbackInfo ci) {
        if (ecr$recipe == null) ecr$recipe = new CachedRecipe<>(RecipeTypeRegistry.INSTANCE.getStructure().get());
        ECEvents.itemEntityTickCraft(this.getItem(), this.ecr$recipe, this.position(), this.level(), this.ecr$ticker);
    }
}
