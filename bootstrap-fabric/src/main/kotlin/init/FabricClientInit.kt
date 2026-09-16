package com.algorithmlx.ecr.fabric.init

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
import com.algorithmlx.ecr.common.init.events.ECEvents
import com.algorithmlx.ecr.fabric.client.FabricConnectedTextures
import com.algorithmlx.ecr.fabric.client.FabricIrisCompatibility
import com.algorithmlx.ecr.fabric.client.MultiblockPreviewGuiBridgeInit
import com.algorithmlx.ecr.network.*
import com.algorithmlx.ecr.registry.BlockEntityTypeRegistry
import com.algorithmlx.ecr.registry.MenuTypeRegistry
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.MenuScreens
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers
import net.minecraft.client.renderer.special.SpecialModelRenderers
import net.minecraft.server.packs.PackType

object FabricClientInit {
    @JvmStatic
    fun init() {
        FabricIrisCompatibility.init()
        SpecialModelRenderers.ID_MAPPER.put(BedrockGeoItemRenderer.ID, BedrockGeoItemRenderer.Unbaked.CODEC)
        FabricConnectedTextures.init()
        ECRConnectedTextures.init()
        registerBedrockParticles()
        registerReceivers()
        registerTooltipEvent()
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            SoulStoneTooltipNetwork.clear()
            MultiblockWorldPreview.clear()
            MagicShieldRenderer.clear()
        }

        MultiblockPreviewGuiBridgeInit.init()
        ResearchBookClient.init()

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

        ModelLayerRegistry.registerModelLayer(MithrilineFurnaceRenderer.MF_LAYER, MithrilineFurnaceRenderer::createBodyLayer)

        MenuScreens.register(MenuTypeRegistry.instance.mithrilineFurnace, ::MithrilineFurnaceScreen)
        MenuScreens.register(MenuTypeRegistry.instance.radiatingChamber, ::RadiatingChamberScreen)
        MenuScreens.register(MenuTypeRegistry.instance.heatGenerator, ::HeatGeneratorScreen)
        MenuScreens.register(MenuTypeRegistry.instance.magicTable, ::MagicTableMenuScreen)
        MenuScreens.register(MenuTypeRegistry.instance.matrixDestructor, ::MatrixDestructorScreen)
        MenuScreens.register(MenuTypeRegistry.instance.enrichmentChamberController, ::EnrichmentChamberControllerScreen)
        MenuScreens.register(MenuTypeRegistry.instance.enrichmentChamberReceiver, ::EnrichmentChamberReceiverScreen)
        MenuScreens.register(MenuTypeRegistry.instance.rayTower, ::RayTowerScreen)
        MenuScreens.register(MenuTypeRegistry.instance.magicalTeleporter, ::MagicalTeleporterScreen)
    }

    private fun registerTooltipEvent() {
        ItemTooltipCallback.EVENT.register { stack, _, _, components ->
            ECEvents.itemTooltip(stack, components)
        }
    }

    private fun registerBedrockParticles() {
        BedrockParticleRenderTypes.init()
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener("bedrock_particles".ecRL, BedrockParticles)
        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener("bedrock_geo".ecRL, BedrockGeoAssets)
        ClientTickEvents.END_LEVEL_TICK.register { level ->
            ClientParticleSystems.get(level)?.update()
            MultiblockWorldPreview.tick(level)
        }
        LevelRenderEvents.COLLECT_SUBMITS.register { context ->
            val minecraft = Minecraft.getInstance()
            val level = minecraft.level ?: return@register
            val poseStack = context.poseStack()
            ClientParticleSystems.get(level)?.submit(
                poseStack,
                context.submitNodeCollector(),
                context.levelState(),
                minecraft.deltaTracker.getGameTimeDeltaPartialTick(false),
                minecraft.player?.uuid,
                minecraft.options.cameraType.isFirstPerson,
            )
            BoundGemLinkRenderer.submit(poseStack, context.submitNodeCollector(), context.levelState())
            MultiblockWorldPreview.submit(poseStack, context.submitNodeCollector(), context.levelState())
            MagicShieldRenderer.submit(poseStack, context.submitNodeCollector(), context.levelState())
            MRULinkRenderer.submit(poseStack, context.submitNodeCollector(), context.levelState())
        }
    }

    private fun registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(GeoBlockAnimationPayload.TYPE) { payload, context ->
            context.client().execute { ClientGeoAnimations.handle(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(GeoEntityAnimationPayload.TYPE) { payload, context ->
            context.client().execute { ClientGeoAnimations.handle(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(GeoItemAnimationPayload.TYPE) { payload, context ->
            context.client().execute { ClientGeoAnimations.handle(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(GeoBlockAnimationStopPayload.TYPE) { payload, context ->
            context.client().execute { ClientGeoAnimations.handle(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(GeoEntityAnimationStopPayload.TYPE) { payload, context ->
            context.client().execute { ClientGeoAnimations.handle(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(GeoItemAnimationStopPayload.TYPE) { payload, context ->
            context.client().execute { ClientGeoAnimations.handle(payload) }
        }

        ClientPlayNetworking.registerGlobalReceiver(ResearchSyncPayload.TYPE) { payload, context ->
            context.client().execute { ClientResearchState.apply(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(ResearchProgressPayload.TYPE) { payload, context ->
            context.client().execute { ClientResearchState.apply(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(BoundGemTooltipResponsePayload.TYPE) { payload, context ->
            context.client().execute { BoundGemTooltipNetwork.acceptResponse(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(SoulStoneTooltipResponsePayload.TYPE) { payload, context ->
            context.client().execute { SoulStoneTooltipNetwork.acceptResponse(payload) }
        }
        ClientPlayNetworking.registerGlobalReceiver(MagicShieldPayload.TYPE) { payload, context ->
            context.client().execute {
                MagicShieldRenderer.accept(payload)
            }
        }

        ResearchNetwork.completeResearch = { ClientPlayNetworking.send(CompleteResearchPayload(it)) }
        ResearchNetwork.updateFavorite =
            { research, spread, color -> ClientPlayNetworking.send(FavoriteResearchPayload(research, spread, color)) }
        ResearchNetwork.updateView = { state ->
            runCatching {
                if (ClientPlayNetworking.canSend(UpdateBookViewPayload.TYPE)) {
                    ClientPlayNetworking.send(UpdateBookViewPayload(state))
                }
            }
        }
        BoundGemTooltipNetwork.currentDimension = { Minecraft.getInstance().level?.dimension() }
        GeoAnimationNetwork.playClientBlockAnimation = ClientGeoAnimations::handle
        GeoAnimationNetwork.playClientEntityAnimation = ClientGeoAnimations::handle
        GeoAnimationNetwork.playClientItemAnimation = ClientGeoAnimations::handle
        GeoAnimationNetwork.stopClientBlockAnimation = ClientGeoAnimations::handle
        GeoAnimationNetwork.stopClientEntityAnimation = ClientGeoAnimations::handle
        GeoAnimationNetwork.stopClientItemAnimation = ClientGeoAnimations::handle
        BoundGemTooltipNetwork.sendRequestToServer = { payload ->
            runCatching {
                if (ClientPlayNetworking.canSend(BoundGemTooltipRequestPayload.TYPE)) {
                    ClientPlayNetworking.send(payload)
                }
            }
        }
        SoulStoneTooltipNetwork.sendRequestToServer = { payload ->
            runCatching {
                if (ClientPlayNetworking.canSend(SoulStoneTooltipRequestPayload.TYPE)) {
                    ClientPlayNetworking.send(payload)
                }
            }
        }
    }
}
