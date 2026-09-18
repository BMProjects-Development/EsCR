package com.algorithmlx.ecr.mixin;

import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblocks;
import com.algorithmlx.ecr.common.magic.MagicDefense;
import com.algorithmlx.ecr.common.magic.MagicDefenseThreadProtect;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    protected LivingEntityMixin(EntityType<?> type, Level level) {
        super(type, level);
    }

    @WrapMethod(method = "hurtServer")
    private boolean applyDefense(ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
        if (MagicDefenseThreadProtect.active()) return original.call(level, source, amount);

        var result = MagicDefense.resolve(
            (LivingEntity) (Object) this,
            level, source, amount
        );

        if (result.getBlocked()) return false;

        return original.call(level, source, result.getDamage());
    }

    @Group(name = "landingParticle", min = 1, max = 1)
    @ModifyArg(
        method = "checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/particles/BlockParticleOption;<init>(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;)V"
        ),
        index = 1,
        require = 0
    )
    private BlockState ecr$useControllerLandingParticleVanilla(BlockState state) {
        return ecr$controllerLandingParticle(state);
    }

    @Group(name = "landingParticle", min = 1, max = 1)
    @ModifyArg(
        method = "checkFallDamage(DZLnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/core/particles/BlockParticleOption;<init>(Lnet/minecraft/core/particles/ParticleType;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;)V"
        ),
        index = 1,
        require = 0
    )
    private BlockState ecr$useControllerLandingParticleNeoForge(BlockState state) {
        return ecr$controllerLandingParticle(state);
    }

    private BlockState ecr$controllerLandingParticle(BlockState state) {
        BlockState controller = AssembledMultiblocks.controllerOriginalState(level(), getOnPosLegacy());
        return controller != null ? controller : state;
    }
}
