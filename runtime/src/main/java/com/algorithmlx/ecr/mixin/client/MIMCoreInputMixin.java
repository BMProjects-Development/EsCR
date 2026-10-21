package com.algorithmlx.ecr.mixin.client;

import com.algorithmlx.ecr.client.screen.MIMScreen;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractContainerScreen.class)
public abstract class MIMCoreInputMixin {
    @WrapOperation(
        method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;handleContainerInput(IIILnet/minecraft/world/inventory/ContainerInput;Lnet/minecraft/world/entity/player/Player;)V")
    )
    private void coreClick(MultiPlayerGameMode gameMode, int containerId, int slotId, int button, ContainerInput input, Player player, Operation<Void> original) {
        if ((Object) this instanceof MIMScreen screen && screen.getMenu().isCore())
            screen.sendCoreClick(slotId, button, input);
        else original.call(gameMode, containerId, slotId, button, input, player);
    }
}
