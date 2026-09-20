package com.algorithmlx.ecr.neoforge.init.registry

import com.algorithmlx.ecr.api.menu.MenuTypePlatform
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.flag.FeatureFlags
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension

object NeoForgeMenuTypePlatform : MenuTypePlatform {
    override fun <M : AbstractContainerMenu> create(factory: (Int, Inventory) -> M): MenuType<M> =
        MenuType(factory, FeatureFlags.VANILLA_SET)

    override fun <M : AbstractContainerMenu, D : Any> create(
        codec: StreamCodec<RegistryFriendlyByteBuf, D>,
        factory: (Int, Inventory, D) -> M,
    ): MenuType<M> = IMenuTypeExtension.create { containerId, inventory, buffer ->
        factory(containerId, inventory, codec.decode(buffer))
    }
}
