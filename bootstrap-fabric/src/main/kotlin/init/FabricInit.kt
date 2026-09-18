package com.algorithmlx.ecr.fabric.init

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.block.JSONBlockProperties
import com.algorithmlx.ecr.api.chunk.ChunkLoadingPlatform
import com.algorithmlx.ecr.api.geo.GeoAnimationNetwork
import com.algorithmlx.ecr.api.geo.GeoBlockAnimationPayload
import com.algorithmlx.ecr.api.geo.GeoBlockAnimationStopPayload
import com.algorithmlx.ecr.api.geo.GeoEntityAnimationPayload
import com.algorithmlx.ecr.api.geo.GeoEntityAnimationStopPayload
import com.algorithmlx.ecr.api.geo.GeoItemAnimationPayload
import com.algorithmlx.ecr.api.geo.GeoItemAnimationStopPayload
import com.algorithmlx.ecr.api.init.MultiblockMatcherTypes
import com.algorithmlx.ecr.api.item.*
import com.algorithmlx.ecr.api.menu.MenuTypeData
import com.algorithmlx.ecr.api.mru.*
import com.algorithmlx.ecr.api.multiblock.MultiblockDataReloadListener
import com.algorithmlx.ecr.api.registries.*
import com.algorithmlx.ecr.api.research.*
import com.algorithmlx.ecr.api.research.content.ResearchAction
import com.algorithmlx.ecr.api.utils.countByIngredient
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.api.utils.openMenuScreenInternal
import com.algorithmlx.ecr.common.components.PlayerMatrixStorage
import com.algorithmlx.ecr.common.init.ECRCommands
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.api.config.ConfigManager
import com.algorithmlx.ecr.common.init.config.ECConfig
import com.algorithmlx.ecr.common.init.events.ECEvents
import com.algorithmlx.ecr.common.init.reload.ResearchReloadListener
import com.algorithmlx.ecr.common.init.reload.SoulStoneDataReloadListener
import com.algorithmlx.ecr.common.item.NamedBlockItem
import com.algorithmlx.ecr.common.research.ResearchConfigDisabler
import com.algorithmlx.ecr.fabric.api.CountIngredient
import com.algorithmlx.ecr.fabric.chunk.FabricChunkLoadingPlatform
import com.algorithmlx.ecr.fabric.init.registry.FabricBlockEntityTypeRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricBlockRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricBookTypeRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricCreativeTabRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricDataComponentRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricItemRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricMRUTypeRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricMenuTypeRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricMobEffectRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricMultiblockMatcherTypes
import com.algorithmlx.ecr.fabric.init.registry.FabricMultiblockRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricAttachmentRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricRecipeDisplayTypeRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricRecipeSerializerRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricRecipeTypeRegistry
import com.algorithmlx.ecr.fabric.init.registry.FabricResearchSerializerRegistry
import com.algorithmlx.ecr.fabric.init.registry.attachments.FabricPlayerMatrixStorage
import com.algorithmlx.ecr.fabric.utils.FabricPlatformUtils
import com.algorithmlx.ecr.network.BoundGemTooltipNetwork
import com.algorithmlx.ecr.network.BoundGemTooltipRequestPayload
import com.algorithmlx.ecr.network.BoundGemTooltipResponsePayload
import com.algorithmlx.ecr.network.MagicShieldNetwork
import com.algorithmlx.ecr.network.MagicShieldPayload
import com.algorithmlx.ecr.network.SoulStoneTooltipNetwork
import com.algorithmlx.ecr.network.SoulStoneTooltipRequestPayload
import com.algorithmlx.ecr.network.SoulStoneTooltipResponsePayload
import com.algorithmlx.ecr.registry.*
import com.algorithmlx.ecr.utils.PlatformUtils
import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.AttackBlockCallback
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseEntityCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.fabricmc.fabric.api.recipe.v1.ingredient.CustomIngredientSerializer
import net.fabricmc.fabric.api.recipe.v1.ingredient.FabricIngredient
import net.fabricmc.fabric.api.resource.v1.ResourceLoader
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.levelgen.GenerationStep
import java.io.File

object FabricInit {
    @JvmStatic
    fun init() {
        JSONBlockProperties.allowNamespace(ModId)
        ECConfig.instance = ConfigManager.saveOrLoad(File("config/$ModId.json"), ECConfig())

        initBuiltinRegistries()
        ResearchConfigDisabler.init()

        registerPayloads()
        registerReloadListener()
        registerProgressEvents()
        registerAccessEvents()
        registerTabEvent()
        registerBoundGemEvents()
        registerEntityEvents()

        initRegistries()
        registerWorldgen()

        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ -> ECRCommands.register(dispatcher) }

        extendPlatform()
    }

    private fun initRegistries() {
        PlatformUtils.instance = FabricPlatformUtils
        ChunkLoadingPlatform.instance = FabricChunkLoadingPlatform
        FabricAttachmentRegistry.init()
        DataComponentRegistry.instance = FabricDataComponentRegistry
        BookTypeRegistry.instance = FabricBookTypeRegistry
        FabricResearchSerializerRegistry.register()
        BlockRegistry.instance = FabricBlockRegistry
        BlockEntityTypeRegistry.instance = FabricBlockEntityTypeRegistry
        ItemRegistry.instance = FabricItemRegistry
        MenuTypeRegistry.instance = FabricMenuTypeRegistry
        MobEffectRegistry.instance = FabricMobEffectRegistry
        MRUTypeRegistry.instance = FabricMRUTypeRegistry
        MultiblockMatcherTypes.instance = FabricMultiblockMatcherTypes
        MultiblockRegistry.instance = FabricMultiblockRegistry
        RecipeDisplayTypeRegistry.instance = FabricRecipeDisplayTypeRegistry
        RecipeSerializerRegistry.instance = FabricRecipeSerializerRegistry
        RecipeTypeRegistry.instance = FabricRecipeTypeRegistry
        CreativeTabRegistry.instance = FabricCreativeTabRegistry

        CustomIngredientSerializer.register(CountIngredient.SERIALIZER)
    }

    private fun initBuiltinRegistries() {
        register(ECRegistryKeys.MRU_TYPE_KEY, ECRegistries.MRU_TYPE)
        register(ECRegistryKeys.MULTIBLOCK_KEY, ECRegistries.MULTIBLOCK)
        register(ECRegistryKeys.ASSEMBLED_MULTIBLOCK_KEY, ECRegistries.ASSEMBLED_MULTIBLOCK)
        register(ECRegistryKeys.BOOK_TYPE_KEY, ECRegistries.BOOK_TYPES)
        register(ECRegistryKeys.BOOK_ELEMENT_SERIALIZER_KEY, ECRegistries.BOOK_ELEMENT_SERIALIZER)
        register(ECRegistryKeys.RESEARCH_TASK_SERIALIZER_KEY, ECRegistries.RESEARCH_TASK_SERIALIZER)
        register(ECRegistryKeys.MULTIBLOCK_MATCHER_TYPE_KEY, ECRegistries.MULTIBLOCK_MATCHER_TYPE)
    }

    private fun registerWorldgen() {
        BiomeModifications.addFeature(
            BiomeSelectors.foundInOverworld(),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            ResourceKey.create(Registries.PLACED_FEATURE, ECRModIDs.MITHRILINE_ORE.ecRL),
        )
    }

    private fun registerPayloads() {
        PayloadTypeRegistry.clientboundPlay().registerLarge(ResearchSyncPayload.TYPE, ResearchSyncPayload.STREAM_CODEC, 8 * 1024 * 1024)
        PayloadTypeRegistry.clientboundPlay().register(ResearchProgressPayload.TYPE, ResearchProgressPayload.STREAM_CODEC)
        PayloadTypeRegistry.serverboundPlay().register(CompleteResearchPayload.TYPE, CompleteResearchPayload.STREAM_CODEC)
        PayloadTypeRegistry.serverboundPlay().register(FavoriteResearchPayload.TYPE, FavoriteResearchPayload.STREAM_CODEC)
        PayloadTypeRegistry.serverboundPlay().register(UpdateBookViewPayload.TYPE, UpdateBookViewPayload.STREAM_CODEC)
        PayloadTypeRegistry.serverboundPlay().register(BoundGemTooltipRequestPayload.TYPE, BoundGemTooltipRequestPayload.STREAM_CODEC)
        PayloadTypeRegistry.clientboundPlay().register(BoundGemTooltipResponsePayload.TYPE, BoundGemTooltipResponsePayload.STREAM_CODEC)
        PayloadTypeRegistry.serverboundPlay().register(SoulStoneTooltipRequestPayload.TYPE, SoulStoneTooltipRequestPayload.STREAM_CODEC)
        PayloadTypeRegistry.clientboundPlay().register(SoulStoneTooltipResponsePayload.TYPE, SoulStoneTooltipResponsePayload.STREAM_CODEC)
        PayloadTypeRegistry.clientboundPlay().register(MagicShieldPayload.TYPE, MagicShieldPayload.STREAM_CODEC)
        PayloadTypeRegistry.clientboundPlay().register(
            GeoBlockAnimationPayload.TYPE,
            GeoBlockAnimationPayload.STREAM_CODEC,
        )
        PayloadTypeRegistry.clientboundPlay().register(
            GeoEntityAnimationPayload.TYPE,
            GeoEntityAnimationPayload.STREAM_CODEC,
        )
        PayloadTypeRegistry.clientboundPlay().register(
            GeoItemAnimationPayload.TYPE,
            GeoItemAnimationPayload.STREAM_CODEC,
        )
        PayloadTypeRegistry.clientboundPlay().register(
            GeoBlockAnimationStopPayload.TYPE,
            GeoBlockAnimationStopPayload.STREAM_CODEC,
        )
        PayloadTypeRegistry.clientboundPlay().register(
            GeoEntityAnimationStopPayload.TYPE,
            GeoEntityAnimationStopPayload.STREAM_CODEC,
        )
        PayloadTypeRegistry.clientboundPlay().register(
            GeoItemAnimationStopPayload.TYPE,
            GeoItemAnimationStopPayload.STREAM_CODEC,
        )

        ServerPlayNetworking.registerGlobalReceiver(CompleteResearchPayload.TYPE) { payload, context ->
            context.server().execute { ResearchProgress.tryUnlock(context.player(), payload.research) }
        }
        ServerPlayNetworking.registerGlobalReceiver(FavoriteResearchPayload.TYPE) { payload, context ->
            context.server().execute { ResearchProgress.setBookmark(context.player(), payload.research, payload.spread, payload.color) }
        }
        ServerPlayNetworking.registerGlobalReceiver(UpdateBookViewPayload.TYPE) { payload, context ->
            context.server().execute { ResearchProgress.updateView(context.player(), payload.state) }
        }
        ServerPlayNetworking.registerGlobalReceiver(BoundGemTooltipRequestPayload.TYPE) { payload, context ->
            context.server().execute { BoundGemTooltipNetwork.handleRequest(context.player(), payload) }
        }
        ServerPlayNetworking.registerGlobalReceiver(SoulStoneTooltipRequestPayload.TYPE) { payload, context ->
            context.server().execute { SoulStoneTooltipNetwork.handleRequest(context.player(), payload) }
        }
    }

    private fun registerReloadListener() {
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
            "multiblocks".ecRL,
            MultiblockDataReloadListener(),
        )
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener("research".ecRL, ResearchReloadListener())
        ResourceLoader.get(PackType.SERVER_DATA).registerReloadListener(
            "settings/${ECRModIDs.SOUL_STONE}".ecRL,
            SoulStoneDataReloadListener(ConfigManager.json),
        )
    }

    private fun registerProgressEvents() {
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register { player, _ -> ResearchProgress.onPlayerJoin(player) }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { server, _, success ->
            if (success) ResearchProgress.syncAll(server)
        }
        ServerTickEvents.END_SERVER_TICK.register { server -> server.playerList.players.forEach(ResearchProgress::tick) }
    }

    private fun registerAccessEvents() {
        UseItemCallback.EVENT.register { player, _, hand ->
            if (ResearchAccess.canAccess(
                    player,
                    player.getItemInHand(hand),
                    ResearchAction.USE,
                )
            ) {
                InteractionResult.PASS
            } else {
                InteractionResult.FAIL
            }
        }
        UseBlockCallback.EVENT.register { player, level, hand, hit ->
            val blockAllowed = ResearchAccess.canAccess(player, level.getBlockState(hit.blockPos), ResearchAction.INTERACT)
            val stack = player.getItemInHand(hand)
            val action = if (stack.item is BlockItem) ResearchAction.PLACE else ResearchAction.USE
            val itemAllowed = ResearchAccess.canAccess(player, stack, action)
            if (blockAllowed && itemAllowed) InteractionResult.PASS else InteractionResult.FAIL
        }
        AttackBlockCallback.EVENT.register { player, level, hand, pos, _ ->
            val blockAllowed = ResearchAccess.canAccess(player, level.getBlockState(pos), ResearchAction.BREAK)
            val itemAllowed = ResearchAccess.canAccess(player, player.getItemInHand(hand), ResearchAction.ATTACK)
            if (blockAllowed && itemAllowed) InteractionResult.PASS else InteractionResult.FAIL
        }
        UseEntityCallback.EVENT.register { player, _, hand, entity, _ ->
            val entityAllowed = ResearchAccess.canAccess(player, entity, ResearchAction.INTERACT)
            val itemAllowed = ResearchAccess.canAccess(player, player.getItemInHand(hand), ResearchAction.USE)
            if (entityAllowed && itemAllowed) InteractionResult.PASS else InteractionResult.FAIL
        }
        AttackEntityCallback.EVENT.register { player, _, hand, entity, _ ->
            val entityAllowed = ResearchAccess.canAccess(player, entity, ResearchAction.ATTACK)
            val itemAllowed = ResearchAccess.canAccess(player, player.getItemInHand(hand), ResearchAction.ATTACK)
            if (entityAllowed && itemAllowed) InteractionResult.PASS else InteractionResult.FAIL
        }
    }

    private fun registerTabEvent() {
        CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register { tab, output ->
            BuiltInRegistries.ITEM.keySet().filter { it.namespace == ModId }.forEach {
                val item = BuiltInRegistries.ITEM.getOptional(it).get()
                if (tab == CreativeTabRegistry.instance.blocks) {
                    if ((item is BlockItem || item is NamedBlockItem) && item.block !is NoTab) {
                        output.accept(item)
                    }
                    return@forEach
                }

                if (BuiltInRegistries.BLOCK.getOptional(it).isPresent) return@forEach

                if (item is NoTab || tab != CreativeTabRegistry.instance.items) return@forEach

                if (item is HasSubItem) {
                    item.addSubItems(ItemStack(item)).forEach { stack ->
                        output.accept(stack)
                    }

                    return@forEach
                }

                output.accept(item)
            }
        }
    }

    private fun registerBoundGemEvents() {
        UseItemCallback.EVENT.register evt@{ player, _, hand ->
            val stack = player.getItemInHand(hand)

            val item = stack.item
            if (item is BoundGem) {
                if (!player.isShiftKeyDown || item.getBoundPos(stack) == null) return@evt InteractionResult.PASS

                player.sendOverlayMessage(Component.translatable("tooltip.$ModId.${ECRModIDs.BOUND_GEM}.revoke"))
                item.setBoundPos(stack, null)

                return@evt InteractionResult.SUCCESS
            }

            InteractionResult.PASS
        }

        UseBlockCallback.EVENT.register evt@{ player, level, hand, hit ->
            val stack = player.getItemInHand(hand)
            val pos = hit.blockPos

            val item = stack.item
            if (item is BoundGem) {
                val device = level.resolveMRUDevice(hit.blockPos)
                if (device == null || !device.deviceType.isConnectable || item.getBoundPos(stack) != null) return@evt InteractionResult.PASS

                player.sendOverlayMessage(
                    Component
                        .translatable("tooltip.$ModId.${ECRModIDs.BOUND_GEM}.linked")
                        .append(": ")
                        .append("X: ${pos.x} Y: ${pos.y} Z: ${pos.z}"),
                )

                if (stack.count > 1) {
                    val copied =
                        stack.copy().apply {
                            this.count = 1
                            item.setBoundPos(this, pos)
                        }

                    stack.shrink(1)

                    val itemEntity =
                        ItemEntity(level, player.x, player.y, player.z, copied).apply {
                            this.setNoPickUpDelay()
                            this.setThrower(player)
                        }

                    level.addFreshEntity(itemEntity)
                } else {
                    item.setBoundPos(stack, pos)
                }

                return@evt InteractionResult.SUCCESS
            }

            InteractionResult.PASS
        }
    }

    private fun registerEntityEvents() {
        ServerLivingEntityEvents.AFTER_DEATH.register(ECEvents::livingDeath)
    }

    private fun extendPlatform() {
        ResearchNetwork.sendToPlayer = ServerPlayNetworking::send
        ResearchNetwork.sendProgressToPlayer = ServerPlayNetworking::send
        BoundGemTooltipNetwork.sendResponseToPlayer = ServerPlayNetworking::send
        SoulStoneTooltipNetwork.sendResponseToPlayer = ServerPlayNetworking::send
        GeoAnimationNetwork.sendToPlayer = ServerPlayNetworking::send
        GeoAnimationNetwork.sendEntityToPlayer = ServerPlayNetworking::send
        GeoAnimationNetwork.sendItemToPlayer = ServerPlayNetworking::send
        GeoAnimationNetwork.sendBlockStopToPlayer = ServerPlayNetworking::send
        GeoAnimationNetwork.sendEntityStopToPlayer = ServerPlayNetworking::send
        GeoAnimationNetwork.sendItemStopToPlayer = ServerPlayNetworking::send
        MagicShieldNetwork.sendToPlayer = ServerPlayNetworking::send

        PlayerMatrixStorage.instance = FabricPlayerMatrixStorage

        countByIngredient = { ((it as FabricIngredient).customIngredient as? CountIngredient)?.count ?: 1 }

        openMenuScreenInternal = menuScreen@{ player, provider, level, pos ->
            if (level.isClientSide) return@menuScreen
            val serverPlayer = player as ServerPlayer
            serverPlayer.openMenu(
                object : ExtendedMenuProvider<MenuTypeData> {
                    override fun getDisplayName(): Component = provider.displayName

                    override fun createMenu(
                        containerId: Int,
                        inventory: Inventory,
                        player: Player,
                    ): AbstractContainerMenu? = provider.createMenu(containerId, inventory, player)

                    override fun getScreenOpeningData(player: ServerPlayer): MenuTypeData = MenuTypeData(pos)
                },
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Registry<*>> register(
        resourceKey: ResourceKey<T>,
        t: T,
    ): T = Registry.register(BuiltInRegistries.REGISTRY as Registry<Registry<*>>, resourceKey.identifier(), t)
}
