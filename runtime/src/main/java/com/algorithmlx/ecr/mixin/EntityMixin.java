package com.algorithmlx.ecr.mixin;

import com.algorithmlx.ecr.common.magic.MagicDefense;
import com.algorithmlx.ecr.common.magic.MagicDefenseThreadProtect;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public class EntityMixin {
    @Redirect(
        method = {"hurt", "hurtOrSimulate"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
        )
    )
    private boolean applyDefense(Entity instance, ServerLevel serverLevel, DamageSource damageSource, float v) {
        var result = MagicDefense.resolve(instance, serverLevel, damageSource, v);

        if (result.getBlocked()) return false;

        MagicDefenseThreadProtect.enter();

        try {
            return instance.hurtServer(serverLevel, damageSource, result.getDamage());
        } finally {
            MagicDefenseThreadProtect.exit();
        }
    }
}
