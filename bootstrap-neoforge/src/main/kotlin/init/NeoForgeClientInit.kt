package com.algorithmlx.ecr.neoforge.init

import com.algorithmlx.ecr.api.client.render.MultiblockPreviewGuiBridge
import com.algorithmlx.ecr.api.client.render.MultiblockPreviewPictureRenderer
import com.algorithmlx.ecr.api.client.render.MultiblockPreviewRenderState
import com.algorithmlx.ecr.api.event.client.AddClientReloadListenersEvent as CrossAddClientReloadListenersEvent
import com.algorithmlx.ecr.api.event.engine.EventBuses
import com.algorithmlx.ecr.api.event.client.ClientPlayerNetworkEvent as CrossClientPlayerNetworkEvent
import com.algorithmlx.ecr.api.event.client.ClientTickEvent as CrossClientTickEvent
import com.algorithmlx.ecr.api.event.client.EntityRenderersEvent as CrossEntityRenderersEvent
import com.algorithmlx.ecr.api.event.client.RegisterMenuScreensEvent as CrossRegisterMenuScreensEvent
import com.algorithmlx.ecr.api.network.ClientNetworkPlatform
import com.algorithmlx.ecr.api.geo.client.BedrockGeoItemRenderer
import com.algorithmlx.ecr.client.renderer.ECRClientRendering
import com.algorithmlx.ecr.init.ECRClientInit
import com.algorithmlx.ecr.neoforge.client.NeoForgeConnectedTextures
import com.algorithmlx.ecr.neoforge.client.NeoForgeIrisCompatibility
import com.algorithmlx.ecr.neoforge.network.NeoForgeClientNetworkPlatform
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.inventory.MenuAccess
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.BlockEntityType
import net.neoforged.bus.api.IEventBus
import net.neoforged.neoforge.client.event.*
import net.neoforged.neoforge.common.NeoForge

object NeoForgeClientInit {
    fun init(bus: IEventBus) {
        ClientNetworkPlatform.instance = NeoForgeClientNetworkPlatform(bus)
        NeoForgeIrisCompatibility.init()
        NeoForgeConnectedTextures.init(bus)
        ECRClientInit.init()
        MultiblockPreviewGuiBridge.install(GuiGraphicsExtractor::submitPictureInPictureRenderState)
        bus.addListener(::onRegisterPIPRenders)
        bus.addListener(::onRegisterClientReloadListeners)
        bus.addListener(::onRegisterSpecialModelRenderer)
        bus.addListener(::onMenuScreen)

        bus.addListener(::onRegisterEntityModelLayer)
        bus.addListener(::onRegisterEntityRenderers)

        NeoForge.EVENT_BUS.addListener(::onClientTick)
        NeoForge.EVENT_BUS.addListener(::onClientTickPre)
        NeoForge.EVENT_BUS.addListener(::onClientLogin)
        NeoForge.EVENT_BUS.addListener(::onClientLogout)
        NeoForge.EVENT_BUS.addListener(::onSubmitCustomGeometry)
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        EventBuses.GAME(CrossClientTickEvent.Post)
    }

    private fun onRegisterClientReloadListeners(event: AddClientReloadListenersEvent) {
        EventBuses.MOD(CrossAddClientReloadListenersEvent(event::addListener))
    }

    private fun onClientTickPre(event: ClientTickEvent.Pre) {
        EventBuses.GAME(CrossClientTickEvent.Pre)
    }

    private fun onClientLogin(event: ClientPlayerNetworkEvent.LoggingIn) {
        EventBuses.GAME(CrossClientPlayerNetworkEvent.LoggingIn)
    }

    private fun onClientLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
        EventBuses.GAME(CrossClientPlayerNetworkEvent.LoggingOut)
    }

    private fun onSubmitCustomGeometry(event: SubmitCustomGeometryEvent) {
        ECRClientRendering.submit(
            event.poseStack,
            event.submitNodeCollector,
            event.levelRenderState
        )
    }

    private fun onRegisterSpecialModelRenderer(event: RegisterSpecialModelRendererEvent) {
        event.register(BedrockGeoItemRenderer.ID, BedrockGeoItemRenderer.Unbaked.CODEC)
    }

    private fun onMenuScreen(event: RegisterMenuScreensEvent) {
        EventBuses.MOD(
            CrossRegisterMenuScreensEvent(
                object : CrossRegisterMenuScreensEvent.MenuScreenRegistrar {
                    override fun <M, S> register(
                        menuType: MenuType<out M>,
                        screenConstructor: CrossRegisterMenuScreensEvent.ScreenConstructor<M, S>
                    ) where M : AbstractContainerMenu, S : Screen, S : MenuAccess<M> {
                        event.register(menuType) { menu, inventory, title ->
                            screenConstructor.create(menu, inventory, title)
                        }
                    }
                }
            )
        )
    }

    private fun onRegisterPIPRenders(event: RegisterPictureInPictureRenderersEvent) {
        event.register(MultiblockPreviewRenderState::class.java, ::MultiblockPreviewPictureRenderer)
    }

    private fun onRegisterEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        EventBuses.MOD(
            CrossEntityRenderersEvent.RegisterRenderers(
                object : CrossEntityRenderersEvent.BlockEntityRendererRegistrar {
                    override fun <T : BlockEntity, S : BlockEntityRenderState> register(
                        blockEntityType: BlockEntityType<out T>,
                        rendererProvider: BlockEntityRendererProvider<T, S>
                    ) = event.registerBlockEntityRenderer(blockEntityType, rendererProvider)
                }
            )
        )
    }

    private fun onRegisterEntityModelLayer(event: EntityRenderersEvent.RegisterLayerDefinitions) {
        EventBuses.MOD(
            CrossEntityRenderersEvent.RegisterLayerDefinitions { layerLocation, supplier ->
                event.registerLayerDefinition(layerLocation, supplier::invoke)
            }
        )
    }
}
