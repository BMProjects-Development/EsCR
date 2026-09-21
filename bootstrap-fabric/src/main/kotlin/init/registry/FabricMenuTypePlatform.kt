package com.algorithmlx.ecr.fabric.init.registry

import com.algorithmlx.ecr.api.menu.MenuTypePlatform
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType

object FabricMenuTypePlatform : MenuTypePlatform {
    override fun <M : AbstractContainerMenu> create(factory: (Int, Inventory) -> M): MenuType<M> =
        MenuType(factory, FeatureFlags.VANILLA_SET)

    override fun <M : AbstractContainerMenu, D : Any> create(
        codec: StreamCodec<RegistryFriendlyByteBuf, D>,
        factory: (Int, Inventory, D) -> M
    ): MenuType<M> = ExtendedMenuType(factory, codec)
}
