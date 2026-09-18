package com.algorithmlx.ecr.mixin.client;

import com.algorithmlx.ecr.api.multiblock.assembled.AssembledMultiblocks;
import com.algorithmlx.ecr.api.block.FullBlockParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientLevel.class)
public class ClientLevelMixin {
    @ModifyVariable(method = "addDestroyBlockEffect", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private BlockState ecr$useControllerDestroyParticle(
        BlockState state,
        BlockPos pos,
        BlockState originalState
    ) {
        return ecr$controllerParticleState(pos, state);
    }

    @ModifyVariable(method = "addDestroyBlockEffect", at = @At("STORE"), name = "shape")
    private VoxelShape ecr$useFullDestroyParticleShape(VoxelShape shape, BlockPos pos, BlockState blockState) {
        ClientLevel level = (ClientLevel) (Object) this;
        BlockState worldState = level.getBlockState(pos);
        if (worldState.getBlock() instanceof FullBlockParticles fbp) {
            if (!fbp.isEnableForPart(level, pos, worldState)) return Shapes.empty();
            return Shapes.block();
        }
        return shape;
    }

    @ModifyVariable(
        method = "addBreakingBlockEffects(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Z)V",
        at = @At("STORE"),
        name = "blockState"
    )
    private BlockState ecr$useControllerBreakingParticle(
        BlockState state,
        BlockPos pos,
        Direction direction
    ) {
        return ecr$controllerParticleState(pos, state);
    }

    @ModifyVariable(
        method = "addBreakingParticles(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;)V",
        at = @At("STORE"),
        name = "shape"
    )
    private AABB ecr$useFullBreakingParticleShape(AABB shape, BlockPos pos, Direction direction) {
        ClientLevel level = (ClientLevel) (Object) this;
        BlockState worldState = level.getBlockState(pos);
        if (worldState.getBlock() instanceof FullBlockParticles fbp && fbp.isEnableForPart(level, pos, worldState)) {
            return Shapes.block().bounds();
        }
        return shape;
    }

    @Unique
    private BlockState ecr$controllerParticleState(BlockPos pos, BlockState fallback) {
        BlockState controller = AssembledMultiblocks.controllerOriginalState(
            (ClientLevel) (Object) this,
            pos
        );
        return controller != null ? controller : fallback;
    }
}
