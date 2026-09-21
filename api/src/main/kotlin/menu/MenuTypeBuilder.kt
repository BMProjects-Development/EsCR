package com.algorithmlx.ecr.api.menu

import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType

interface MenuTypePlatform {
    fun <M : AbstractContainerMenu> create(factory: (Int, Inventory) -> M): MenuType<M>

    fun <M : AbstractContainerMenu, D : Any> create(
        codec: StreamCodec<RegistryFriendlyByteBuf, D>,
        factory: (Int, Inventory, D) -> M
    ): MenuType<M>

    companion object {
        @JvmStatic
        lateinit var instance: MenuTypePlatform
    }
}

fun <M : AbstractContainerMenu> menuType(factory: (Int, Inventory) -> M): MenuType<M> =
    MenuTypePlatform.instance.create(factory)

fun <M : AbstractContainerMenu, D : Any> menuType(
    codec: StreamCodec<RegistryFriendlyByteBuf, D>,
    factory: (Int, Inventory, D) -> M
): MenuType<M> = MenuTypePlatform.instance.create(codec, factory)
