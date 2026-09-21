package com.algorithmlx.ecr.fabric.init

import com.algorithmlx.ecr.api.event.client.AddClientReloadListenersEvent
import com.algorithmlx.ecr.api.event.client.ClientPlayerNetworkEvent
import com.algorithmlx.ecr.api.event.client.ClientTickEvent
import com.algorithmlx.ecr.api.event.client.EntityRenderersEvent
import com.algorithmlx.ecr.api.event.client.RegisterMenuScreensEvent
import com.algorithmlx.ecr.api.event.engine.EventBuses
import com.algorithmlx.ecr.api.event.entity.player.ItemTooltipEvent
import com.algorithmlx.ecr.api.geo.client.BedrockGeoItemRenderer
import com.algorithmlx.ecr.api.network.ClientNetworkPlatform
import com.algorithmlx.ecr.client.renderer.ECRClientRendering
import com.algorithmlx.ecr.fabric.client.FabricConnectedTextures
import com.algorithmlx.ecr.fabric.client.FabricIrisCompatibility
import com.algorithmlx.ecr.fabric.client.MultiblockPreviewGuiBridgeInit
import com.algorithmlx.ecr.fabric.network.FabricClientNetworkPlatform
import com.algorithmlx.ecr.init.ECRClientInit
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.inventory.MenuAccess
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.client.renderer.special.SpecialModelRenderers
import net.minecraft.core.component.DataComponents
import net.minecraft.server.packs.PackType
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.component.TooltipDisplay
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType

object FabricClientInit {
    @JvmStatic
    fun init() {
        ClientNetworkPlatform.instance = FabricClientNetworkPlatform
        FabricIrisCompatibility.init()
        SpecialModelRenderers.ID_MAPPER.put(BedrockGeoItemRenderer.ID, BedrockGeoItemRenderer.Unbaked.CODEC)
        FabricConnectedTextures.init()
        ECRClientInit.init()
        registerReloadListeners()
        registerRendering()
        registerTooltipEvent()
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            EventBuses.GAME(ClientPlayerNetworkEvent.LoggingOut)
        }
        ClientPlayConnectionEvents.JOIN.register { _, _, _ -> EventBuses.GAME(ClientPlayerNetworkEvent.LoggingIn) }
        ClientTickEvents.START_CLIENT_TICK.register { EventBuses.GAME(ClientTickEvent.Pre) }
        ClientTickEvents.END_CLIENT_TICK.register { EventBuses.GAME(ClientTickEvent.Post) }

        MultiblockPreviewGuiBridgeInit.init()
        registerClientExtensions()
    }

    private fun registerTooltipEvent() {
        ItemTooltipCallback.EVENT.register { stack, context, flags, components ->
            EventBuses.GAME(
                ItemTooltipEvent(
                    stack,
                    Minecraft.getInstance().player,
                    components,
                    flags,
                    context,
                    stack.getOrDefault(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT)
                )
            )
        }
    }

    private fun registerReloadListeners() {
        val loader = ResourceLoader.get(PackType.CLIENT_RESOURCES)
        EventBuses.MOD(AddClientReloadListenersEvent(loader::registerReloadListener))
    }

    private fun registerRendering() {
        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            ECRClientRendering.submit(
                context.poseStack(),
                context.submitNodeCollector(),
                context.levelState()
            )
        }
    }

    private fun registerClientExtensions() {
        EventBuses.MOD(
            EntityRenderersEvent.RegisterRenderers(
                object : EntityRenderersEvent.BlockEntityRendererRegistrar {
                    override fun <T : BlockEntity, S : BlockEntityRenderState> register(
                        blockEntityType: BlockEntityType<out T>,
                        rendererProvider: BlockEntityRendererProvider<T, S>
                    ) = BlockEntityRenderers.register(blockEntityType, rendererProvider)
                }
            )
        )
        EventBuses.MOD(
            EntityRenderersEvent.RegisterLayerDefinitions { layerLocation, supplier ->
                ModelLayerRegistry.registerModelLayer(layerLocation, supplier::invoke)
            }
        )
        EventBuses.MOD(
            RegisterMenuScreensEvent(
                object : RegisterMenuScreensEvent.MenuScreenRegistrar {
                    override fun <M, S> register(
                        menuType: MenuType<out M>,
                        screenConstructor: RegisterMenuScreensEvent.ScreenConstructor<M, S>
                    ) where M : AbstractContainerMenu, S : Screen, S : MenuAccess<M> {
                        MenuScreens.register(menuType) { menu, inventory, title ->
                            screenConstructor.create(menu, inventory, title)
                        }
                    }
                }
            )
        )
    }
}
