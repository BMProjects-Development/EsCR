package com.algorithmlx.ecr.init

import com.algorithmlx.ecr.api.client.render.MultiblockWorldPreview
import com.algorithmlx.ecr.api.event.client.AddClientReloadListenersEvent
import com.algorithmlx.ecr.api.event.client.ClientPlayerNetworkEvent
import com.algorithmlx.ecr.api.event.client.ClientTickEvent
import com.algorithmlx.ecr.api.event.client.EntityRenderersEvent
import com.algorithmlx.ecr.api.event.client.RegisterMenuScreensEvent
import com.algorithmlx.ecr.api.event.engine.EventBuses
import com.algorithmlx.ecr.api.event.engine.listeners
import com.algorithmlx.ecr.api.geo.GeoAnimationNetwork
import com.algorithmlx.ecr.api.geo.client.ClientGeoAnimations
import com.algorithmlx.ecr.api.geo.client.BedrockGeoAssets
import com.algorithmlx.ecr.api.particle.BedrockParticleRenderTypes
import com.algorithmlx.ecr.api.particle.BedrockParticles
import com.algorithmlx.ecr.api.particle.ClientParticleSystems
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.client.ECRConnectedTextures
import com.algorithmlx.ecr.client.book.ResearchBookClient
import com.algorithmlx.ecr.client.renderer.AssembledMultiblockRenderer
import com.algorithmlx.ecr.client.renderer.EnrichmentChamberControllerRenderer
import com.algorithmlx.ecr.client.renderer.MagicShieldRenderer
import com.algorithmlx.ecr.client.renderer.MatrixDestructorRenderer
import com.algorithmlx.ecr.client.renderer.MithrilineFurnaceRenderer
import com.algorithmlx.ecr.client.screen.EnrichmentChamberControllerScreen
import com.algorithmlx.ecr.client.screen.EnrichmentChamberReceiverScreen
import com.algorithmlx.ecr.client.screen.HeatGeneratorScreen
import com.algorithmlx.ecr.client.screen.MagicTableMenuScreen
import com.algorithmlx.ecr.client.screen.MagicalTeleporterScreen
import com.algorithmlx.ecr.client.screen.MatrixDestructorScreen
import com.algorithmlx.ecr.client.screen.MithrilineFurnaceScreen
import com.algorithmlx.ecr.client.screen.RadiatingChamberScreen
import com.algorithmlx.ecr.client.screen.RayTowerScreen
import com.algorithmlx.ecr.network.BoundGemTooltipNetwork
import com.algorithmlx.ecr.network.ECRClientPackets
import com.algorithmlx.ecr.network.SoulStoneTooltipNetwork
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.MenuTypeRegistry
import net.minecraft.client.Minecraft

object ECRClientInit {
    private var initialized = false

    fun init() {
        check(!initialized) { "Client is already initialized" }
        initialized = true

        ECRConnectedTextures.init()
        BedrockParticleRenderTypes.init()
        ECRClientPackets.init()
        ResearchBookClient.init()
        registerReceivers()
        registerEventHandlers()
    }

    private fun registerEventHandlers() {
        EventBuses.GAME.listeners {
            subscribe<ClientTickEvent.Post> {
                Minecraft.getInstance().level?.let { level ->
                    ClientParticleSystems.get(level)?.update()
                    MultiblockWorldPreview.tick(level)
                }
            }
            subscribe<ClientPlayerNetworkEvent.LoggingOut> {
                SoulStoneTooltipNetwork.clear()
                MultiblockWorldPreview.clear()
                MagicShieldRenderer.clear()
            }
        }

        EventBuses.MOD.listeners {
            subscribe<EntityRenderersEvent.RegisterRenderers> { registerRenderers() }
            subscribe<EntityRenderersEvent.RegisterLayerDefinitions> { registerLayerDefinitions() }
            subscribe<RegisterMenuScreensEvent> { registerScreens() }
            subscribe<AddClientReloadListenersEvent> { registerReloadListeners() }
        }
    }

    private fun registerReceivers() {
        BoundGemTooltipNetwork.currentDimension = { Minecraft.getInstance().level?.dimension() }
        GeoAnimationNetwork.playClientBlockAnimation = ClientGeoAnimations::handle
        GeoAnimationNetwork.playClientEntityAnimation = ClientGeoAnimations::handle
        GeoAnimationNetwork.playClientItemAnimation = ClientGeoAnimations::handle
        GeoAnimationNetwork.stopClientBlockAnimation = ClientGeoAnimations::handle
        GeoAnimationNetwork.stopClientEntityAnimation = ClientGeoAnimations::handle
        GeoAnimationNetwork.stopClientItemAnimation = ClientGeoAnimations::handle
    }

    private fun EntityRenderersEvent.RegisterRenderers.registerRenderers() {
        registerBlockEntityRenderer(BlockEntityTypeRegistry.mithrilineFurnace.get(), ::MithrilineFurnaceRenderer)
        registerBlockEntityRenderer(BlockEntityTypeRegistry.assembledMultiblockPart.get(), ::AssembledMultiblockRenderer)
        registerBlockEntityRenderer(BlockEntityTypeRegistry.rayTower.get(), ::AssembledMultiblockRenderer)
        registerBlockEntityRenderer(BlockEntityTypeRegistry.matrixDestructor.get(), ::MatrixDestructorRenderer)
        registerBlockEntityRenderer(
            BlockEntityTypeRegistry.enrichmentChamberController.get(),
            ::EnrichmentChamberControllerRenderer
        )
    }

    private fun EntityRenderersEvent.RegisterLayerDefinitions.registerLayerDefinitions() {
        registerLayerDefinition(MithrilineFurnaceRenderer.MF_LAYER, MithrilineFurnaceRenderer::createBodyLayer)
    }

    private fun RegisterMenuScreensEvent.registerScreens() {
        register(MenuTypeRegistry.mithrilineFurnace, ::MithrilineFurnaceScreen)
        register(MenuTypeRegistry.radiatingChamber, ::RadiatingChamberScreen)
        register(MenuTypeRegistry.heatGenerator, ::HeatGeneratorScreen)
        register(MenuTypeRegistry.magicTable, ::MagicTableMenuScreen)
        register(MenuTypeRegistry.matrixDestructor, ::MatrixDestructorScreen)
        register(MenuTypeRegistry.enrichmentChamberController, ::EnrichmentChamberControllerScreen)
        register(MenuTypeRegistry.enrichmentChamberReceiver, ::EnrichmentChamberReceiverScreen)
        register(MenuTypeRegistry.rayTower, ::RayTowerScreen)
        register(MenuTypeRegistry.magicalTeleporter, ::MagicalTeleporterScreen)
    }

    private fun AddClientReloadListenersEvent.registerReloadListeners() {
        addListener("bedrock_particles".ecRL, BedrockParticles)
        addListener("bedrock_geo".ecRL, BedrockGeoAssets)
    }
}
