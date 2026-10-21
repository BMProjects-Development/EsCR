package com.algorithmlx.ecr.fabric.init.registry

import com.algorithmlx.ecr.api.menu.MenuTypePlatform
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.chat.Component
import net.minecraft.network.codec.StreamCodec
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
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

    override fun <D : Any> open(
        player: ServerPlayer,
        provider: MenuProvider,
        data: D,
        codec: StreamCodec<RegistryFriendlyByteBuf, D>
    ) {
        player.openMenu(object : ExtendedMenuProvider<D> {
            override fun getDisplayName(): Component = provider.displayName

            override fun createMenu(containerId: Int, inventory: Inventory, player: Player): AbstractContainerMenu? =
                provider.createMenu(containerId, inventory, player)

            override fun getScreenOpeningData(player: ServerPlayer): D = data
        })
    }
}
