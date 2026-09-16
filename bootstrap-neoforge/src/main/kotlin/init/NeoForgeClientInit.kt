package com.algorithmlx.ecr.neoforge.init

import com.algorithmlx.ecr.api.client.render.MultiblockPreviewGuiBridge
import com.algorithmlx.ecr.api.client.render.MultiblockPreviewPictureRenderer
import com.algorithmlx.ecr.api.client.render.MultiblockPreviewRenderState
import com.algorithmlx.ecr.api.client.render.MultiblockWorldPreview
import com.algorithmlx.ecr.api.geo.*
import com.algorithmlx.ecr.api.geo.client.BedrockGeoAssets
import com.algorithmlx.ecr.api.geo.client.BedrockGeoItemRenderer
import com.algorithmlx.ecr.api.geo.client.ClientGeoAnimations
import com.algorithmlx.ecr.api.particle.BedrockParticleRenderTypes
import com.algorithmlx.ecr.api.particle.BedrockParticles
import com.algorithmlx.ecr.api.particle.ClientParticleSystems
import com.algorithmlx.ecr.api.research.*
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.client.ECRConnectedTextures
import com.algorithmlx.ecr.client.book.ResearchBookClient
import com.algorithmlx.ecr.client.renderer.*
import com.algorithmlx.ecr.client.screen.*
import com.algorithmlx.ecr.neoforge.client.NeoForgeConnectedTextures
import com.algorithmlx.ecr.neoforge.client.NeoForgeIrisCompatibility
import com.algorithmlx.ecr.network.*
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.MenuTypeRegistry
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent
import net.neoforged.neoforge.client.event.*
import net.neoforged.neoforge.client.network.ClientPacketDistributor
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent
import net.neoforged.neoforge.common.NeoForge

object NeoForgeClientInit {
    fun init(bus: IEventBus) {
        NeoForgeIrisCompatibility.init()
        NeoForgeConnectedTextures.init(bus)
        ECRConnectedTextures.init()
        BedrockParticleRenderTypes.init()
        MultiblockPreviewGuiBridge.install(GuiGraphicsExtractor::submitPictureInPictureRenderState)
        bus.addListener(::onRegisterPIPRenders)
        bus.addListener(::onRegisterClientReloadListeners)

        bus.addListener(::onRegisterClientPayloads)
        bus.addListener(::onRegisterSpecialModelRenderer)
        bus.addListener(::onClientInit)
        bus.addListener(::onMenuScreen)

        bus.addListener(::onRegisterEntityModelLayer)
        bus.addListener(::onRegisterEntityRenderers)

        NeoForge.EVENT_BUS.addListener(::onClientTick)
        NeoForge.EVENT_BUS.addListener(::onClientLogout)
        NeoForge.EVENT_BUS.addListener(::onSubmitCustomGeometry)
    }

    private fun onRegisterClientReloadListeners(event: AddClientReloadListenersEvent) {
        event.addListener("bedrock_particles".ecRL, BedrockParticles)
        event.addListener("bedrock_geo".ecRL, BedrockGeoAssets)
    }

    private fun onClientTick(event: ClientTickEvent.Post) {
        Minecraft.getInstance().level?.let {
            ClientParticleSystems.get(it)?.update()
            MultiblockWorldPreview.tick(it)
        }
    }

    private fun onClientLogout(event: ClientPlayerNetworkEvent.LoggingOut) {
        SoulStoneTooltipNetwork.clear()
        MultiblockWorldPreview.clear()
        MagicShieldRenderer.clear()
    }

    private fun onSubmitCustomGeometry(event: SubmitCustomGeometryEvent) {
        val minecraft = Minecraft.getInstance()
        val level = minecraft.level ?: return
        ClientParticleSystems.get(level)?.submit(
            event.poseStack,
            event.submitNodeCollector,
            event.levelRenderState,
            minecraft.deltaTracker.getGameTimeDeltaPartialTick(false),
            minecraft.player?.uuid,
            minecraft.options.cameraType.isFirstPerson,
        )
        BoundGemLinkRenderer.submit(event.poseStack, event.submitNodeCollector, event.levelRenderState)
        MultiblockWorldPreview.submit(event.poseStack, event.submitNodeCollector, event.levelRenderState)
        MagicShieldRenderer.submit(event.poseStack, event.submitNodeCollector, event.levelRenderState)
        MRULinkRenderer.submit(event.poseStack, event.submitNodeCollector, event.levelRenderState)
    }

    private fun onRegisterSpecialModelRenderer(event: RegisterSpecialModelRendererEvent) {
        event.register(BedrockGeoItemRenderer.ID, BedrockGeoItemRenderer.Unbaked.CODEC)
    }

    private fun onClientInit(event: FMLClientSetupEvent) {
        event.enqueueWork {
            ResearchBookClient.init()

            ResearchNetwork.completeResearch = { ClientPacketDistributor.sendToServer(CompleteResearchPayload(it)) }
            ResearchNetwork.updateFavorite =
                { research, spread, color -> ClientPacketDistributor.sendToServer(FavoriteResearchPayload(research, spread, color)) }
            ResearchNetwork.updateView = { state -> runCatching { ClientPacketDistributor.sendToServer(UpdateBookViewPayload(state)) } }
            BoundGemTooltipNetwork.currentDimension = { Minecraft.getInstance().level?.dimension() }
            BoundGemTooltipNetwork.sendRequestToServer = { payload -> runCatching { ClientPacketDistributor.sendToServer(payload) } }
            SoulStoneTooltipNetwork.sendRequestToServer = { payload -> runCatching { ClientPacketDistributor.sendToServer(payload) } }
            GeoAnimationNetwork.playClientBlockAnimation = ClientGeoAnimations::handle
            GeoAnimationNetwork.playClientEntityAnimation = ClientGeoAnimations::handle
            GeoAnimationNetwork.playClientItemAnimation = ClientGeoAnimations::handle
            GeoAnimationNetwork.stopClientBlockAnimation = ClientGeoAnimations::handle
            GeoAnimationNetwork.stopClientEntityAnimation = ClientGeoAnimations::handle
            GeoAnimationNetwork.stopClientItemAnimation = ClientGeoAnimations::handle

            BlockEntityRenderers.register(BlockEntityTypeRegistry.instance.mithrilineFurnace, ::MithrilineFurnaceRenderer)
            BlockEntityRenderers.register(
                BlockEntityTypeRegistry.instance.assembledMultiblockPart,
                ::AssembledMultiblockRenderer,
            )
            BlockEntityRenderers.register(
                BlockEntityTypeRegistry.instance.rayTower,
                ::AssembledMultiblockRenderer,
            )
            BlockEntityRenderers.register(BlockEntityTypeRegistry.instance.matrixDestructor, ::MatrixDestructorRenderer)
            BlockEntityRenderers.register(
                BlockEntityTypeRegistry.instance.enrichmentChamberController,
                ::EnrichmentChamberControllerRenderer,
            )
        }
    }

    private fun onMenuScreen(event: RegisterMenuScreensEvent) {
        event.register(MenuTypeRegistry.instance.mithrilineFurnace, ::MithrilineFurnaceScreen)
        event.register(MenuTypeRegistry.instance.radiatingChamber, ::RadiatingChamberScreen)
        event.register(MenuTypeRegistry.instance.heatGenerator, ::HeatGeneratorScreen)
        event.register(MenuTypeRegistry.instance.magicTable, ::MagicTableMenuScreen)
        event.register(MenuTypeRegistry.instance.matrixDestructor, ::MatrixDestructorScreen)
        event.register(MenuTypeRegistry.instance.enrichmentChamberController, ::EnrichmentChamberControllerScreen)
        event.register(MenuTypeRegistry.instance.enrichmentChamberReceiver, ::EnrichmentChamberReceiverScreen)
        event.register(MenuTypeRegistry.instance.rayTower, ::RayTowerScreen)
        event.register(MenuTypeRegistry.instance.magicalTeleporter, ::MagicalTeleporterScreen)
    }

    private fun onRegisterClientPayloads(event: RegisterClientPayloadHandlersEvent) {
        event.register(ResearchSyncPayload.TYPE) { payload, _ -> ClientResearchState.apply(payload) }
        event.register(ResearchProgressPayload.TYPE) { payload, _ -> ClientResearchState.apply(payload) }
        event.register(BoundGemTooltipResponsePayload.TYPE) { payload, _ -> BoundGemTooltipNetwork.acceptResponse(payload) }
        event.register(SoulStoneTooltipResponsePayload.TYPE) { payload, _ -> SoulStoneTooltipNetwork.acceptResponse(payload) }
        event.register(GeoBlockAnimationPayload.TYPE) { payload, _ -> ClientGeoAnimations.handle(payload) }
        event.register(GeoEntityAnimationPayload.TYPE) { payload, _ -> ClientGeoAnimations.handle(payload) }
        event.register(GeoItemAnimationPayload.TYPE) { payload, _ -> ClientGeoAnimations.handle(payload) }
        event.register(GeoBlockAnimationStopPayload.TYPE) { payload, _ -> ClientGeoAnimations.handle(payload) }
        event.register(GeoEntityAnimationStopPayload.TYPE) { payload, _ -> ClientGeoAnimations.handle(payload) }
        event.register(GeoItemAnimationStopPayload.TYPE) { payload, _ -> ClientGeoAnimations.handle(payload) }
        event.register(MagicShieldPayload.TYPE) { payload, _ -> MagicShieldRenderer.accept(payload) }
    }

    private fun onRegisterPIPRenders(event: RegisterPictureInPictureRenderersEvent) {
        event.register(MultiblockPreviewRenderState::class.java, ::MultiblockPreviewPictureRenderer)
    }

    private fun onRegisterEntityRenderers(event: EntityRenderersEvent.RegisterRenderers) {
        event.registerBlockEntityRenderer(BlockEntityTypeRegistry.instance.mithrilineFurnace, ::MithrilineFurnaceRenderer)
    }

    private fun onRegisterEntityModelLayer(event: EntityRenderersEvent.RegisterLayerDefinitions) {
        event.registerLayerDefinition(MithrilineFurnaceRenderer.MF_LAYER, MithrilineFurnaceRenderer::createBodyLayer)
    }
}
