package com.algorithmlx.ecr.api.event.client

import com.algorithmlx.ecr.api.event.engine.Event
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.MenuAccess
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType

class RegisterMenuScreensEvent(
    private val registrar: MenuScreenRegistrar
): Event() {
    fun <M, S> register(
        menuType: MenuType<out M>, screenConstructor: ScreenConstructor<M, S>
    ) where M : AbstractContainerMenu, S : Screen, S : MenuAccess<M> = registrar.register(menuType, screenConstructor)

    interface MenuScreenRegistrar {
        fun <M, S> register(
            menuType: MenuType<out M>, screenConstructor: ScreenConstructor<M, S>
        ) where M : AbstractContainerMenu, S : Screen, S : MenuAccess<M>
    }

    fun interface ScreenConstructor<M, S> where M : AbstractContainerMenu, S : Screen, S : MenuAccess<M> {
        fun create(menu: M, inventory: Inventory, title: Component): S
    }
}
