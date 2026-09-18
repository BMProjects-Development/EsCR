package com.algorithmlx.ecr.neoforge.init

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.block.JSONBlockProperties
import com.algorithmlx.ecr.api.chunk.ChunkLoadingPlatform
import com.algorithmlx.ecr.api.geo.*
import com.algorithmlx.ecr.api.init.MultiblockMatcherTypes
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.item.HasSubItem
import com.algorithmlx.ecr.api.item.NoTab
import com.algorithmlx.ecr.api.mru.resolveMRUDevice
import com.algorithmlx.ecr.api.multiblock.MultiblockDataReloadListener
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.research.*
import com.algorithmlx.ecr.api.research.content.ResearchAction
import com.algorithmlx.ecr.api.utils.*
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
import com.algorithmlx.ecr.neoforge.api.CountIngredient
import com.algorithmlx.ecr.neoforge.chunk.NeoForgeChunkLoadingPlatform
import com.algorithmlx.ecr.neoforge.init.registry.*
import com.algorithmlx.ecr.neoforge.init.registry.attachments.NeoForgePlayerMatrixStorage
import com.algorithmlx.ecr.neoforge.utils.NeoForgePlatformUtils
import com.algorithmlx.ecr.network.*
import com.algorithmlx.ecr.registry.*
import com.algorithmlx.ecr.utils.PlatformUtils
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.loading.FMLEnvironment
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.AddServerReloadListenersEvent
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent
import net.neoforged.neoforge.event.OnDatapackSyncEvent
import net.neoforged.neoforge.event.RegisterCommandsEvent
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent
import net.neoforged.neoforge.registries.NewRegistryEvent
import net.neoforged.neoforge.resource.ListenerKey
import java.io.File

object NeoForgeInit {
    fun init(bus: IEventBus) {
        JSONBlockProperties.allowNamespace(ModId)
        ECConfig.instance = ConfigManager.saveOrLoad(File("config/$ModId.json"), ECConfig())

        val forgeBus = NeoForge.EVENT_BUS
        ResearchConfigDisabler.init()

        initRegistries(bus)

        forgeBus.addListener(::onItemTooltip)
        bus.addListener(::onRegisterPayload)

        bus.addListener(::onNewRegistry)
        bus.addListener(::onCreativeTabs)

        forgeBus.addListener(::onAddReloadListener)
        forgeBus.addListener(::onDatapackSync)
        forgeBus.addListener(::onRightClickItemInteract)
        forgeBus.addListener(::onRightClickBlockInteract)
        forgeBus.addListener(::onLeftClickBlock)
        forgeBus.addListener(::onEntityInteract)
        forgeBus.addListener(::onAttackEntityEvent)
        forgeBus.addListener(::onPlayerTick)
        forgeBus.addListener(::onRegisterCommands)
        forgeBus.addListener(::onLivingDeath)

        if (FMLEnvironment.getDist().isClient) {
            NeoForgeClientInit.init(bus)
        }

        extendPlatform()
    }

    private fun initRegistries(bus: IEventBus) {
        PlatformUtils.instance = NeoForgePlatformUtils
        ChunkLoadingPlatform.instance = NeoForgeChunkLoadingPlatform(bus)
        RecipeSerializerRegistry.instance = NeoForgeRecipeSerializerRegistry(bus)
        RecipeTypeRegistry.instance = NeoForgeRecipeTypeRegistry(bus)
        BlockEntityTypeRegistry.instance = NeoForgeBlockEntityTypeRegistry(bus)
        BookTypeRegistry.instance = NeoForgeBookTypeRegistry(bus)
        NeoForgeResearchSerializerRegistry(bus)
        BlockRegistry.instance = NeoForgeBlockRegistry(bus)
        DataComponentRegistry.instance = NeoForgeDataComponentRegistry(bus)
        ItemRegistry.instance = NeoForgeItemRegistry(bus)
        CreativeTabRegistry.instance = NeoForgeCreativeTabRegistry(bus)
        MenuTypeRegistry.instance = NeoForgeMenuTypeRegistry(bus)
        MobEffectRegistry.instance = NeoForgeMobEffectRegistry(bus)
        MRUTypeRegistry.instance = NeoForgeMRUTypeRegistry(bus)
        MultiblockMatcherTypes.instance = NeoForgeMultiblockMatcherTypes(bus)
        MultiblockRegistry.instance = NeoForgeMultiblockRegistry(bus)
        RecipeDisplayTypeRegistry.instance = NeoForgeRecipeDisplayTypeRegistry(bus)
        IngredientRegistry.init(bus)
        NeoForgeAttachmentRegistry.init(bus)
    }

    private fun onNewRegistry(event: NewRegistryEvent) {
        event.register(ECRegistries.MULTIBLOCK)
        event.register(ECRegistries.ASSEMBLED_MULTIBLOCK)
        event.register(ECRegistries.MRU_TYPE)
        event.register(ECRegistries.BOOK_TYPES)
        event.register(ECRegistries.BOOK_ELEMENT_SERIALIZER)
        event.register(ECRegistries.RESEARCH_TASK_SERIALIZER)
        event.register(ECRegistries.MULTIBLOCK_MATCHER_TYPE)
    }

    private fun onItemTooltip(event: ItemTooltipEvent) {
        ECEvents.itemTooltip(event.itemStack, event.toolTip)
    }

    private fun onCreativeTabs(event: BuildCreativeModeTabContentsEvent) {
        BuiltInRegistries.ITEM.keySet().filter { it.namespace == ModId }.forEach {
            val item = BuiltInRegistries.ITEM.getOptional(it).get()
            if (event.tab == CreativeTabRegistry.instance.blocks) {
                if ((item is BlockItem || item is NamedBlockItem) && item.block !is NoTab) {
                    event.accept(item)
                }
                return@forEach
            }

            if (BuiltInRegistries.BLOCK.getOptional(it).isPresent) return@forEach

            if (item is NoTab || event.tab != CreativeTabRegistry.instance.items) return@forEach

            if (item is HasSubItem) {
                item.addSubItems(ItemStack(item)).forEach { stack ->
                    event.accept(stack)
                }

                return@forEach
            }

            event.accept(item)
        }
    }

    private fun onRegisterPayload(event: RegisterPayloadHandlersEvent) {
        val registrar = event.registrar(ModId)
        registrar.playToClient(GeoBlockAnimationPayload.TYPE, GeoBlockAnimationPayload.STREAM_CODEC)
        registrar.playToClient(GeoEntityAnimationPayload.TYPE, GeoEntityAnimationPayload.STREAM_CODEC)
        registrar.playToClient(GeoItemAnimationPayload.TYPE, GeoItemAnimationPayload.STREAM_CODEC)
        registrar.playToClient(GeoBlockAnimationStopPayload.TYPE, GeoBlockAnimationStopPayload.STREAM_CODEC)
        registrar.playToClient(GeoEntityAnimationStopPayload.TYPE, GeoEntityAnimationStopPayload.STREAM_CODEC)
        registrar.playToClient(GeoItemAnimationStopPayload.TYPE, GeoItemAnimationStopPayload.STREAM_CODEC)
        registrar.playToClient(BoundGemTooltipResponsePayload.TYPE, BoundGemTooltipResponsePayload.STREAM_CODEC)
        registrar.playToClient(SoulStoneTooltipResponsePayload.TYPE, SoulStoneTooltipResponsePayload.STREAM_CODEC)
        registrar.playToClient(MagicShieldPayload.TYPE, MagicShieldPayload.STREAM_CODEC)

        registrar.playToClient(ResearchSyncPayload.TYPE, ResearchSyncPayload.STREAM_CODEC)
        registrar.playToClient(ResearchProgressPayload.TYPE, ResearchProgressPayload.STREAM_CODEC)
        registrar.playToServer(CompleteResearchPayload.TYPE, CompleteResearchPayload.STREAM_CODEC) { payload, context ->
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                ResearchProgress.tryUnlock(player, payload.research)
            }
        }
        registrar.playToServer(FavoriteResearchPayload.TYPE, FavoriteResearchPayload.STREAM_CODEC) { payload, context ->
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                ResearchProgress.setBookmark(player, payload.research, payload.spread, payload.color)
            }
        }
        registrar.playToServer(UpdateBookViewPayload.TYPE, UpdateBookViewPayload.STREAM_CODEC) { payload, context ->
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                ResearchProgress.updateView(player, payload.state)
            }
        }
        registrar.playToServer(BoundGemTooltipRequestPayload.TYPE, BoundGemTooltipRequestPayload.STREAM_CODEC) { payload, context ->
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                BoundGemTooltipNetwork.handleRequest(player, payload)
            }
        }
        registrar.playToServer(SoulStoneTooltipRequestPayload.TYPE, SoulStoneTooltipRequestPayload.STREAM_CODEC) { payload, context ->
            context.enqueueWork {
                val player = context.player() as? ServerPlayer ?: return@enqueueWork
                SoulStoneTooltipNetwork.handleRequest(player, payload)
            }
        }
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        ECRCommands.register(event.dispatcher)
    }

    private fun onAddReloadListener(event: AddServerReloadListenersEvent) {
        event.addRetainedListener(
            ListenerKey.create("multiblocks".ecRL),
            MultiblockDataReloadListener(),
        )
        event.addRetainedListener(ListenerKey.create("research".ecRL), ResearchReloadListener())
        event.addRetainedListener(
            ListenerKey.create("settings/${ECRModIDs.SOUL_STONE}".ecRL),
            SoulStoneDataReloadListener(ConfigManager.json),
        )
    }

    private fun onDatapackSync(event: OnDatapackSyncEvent) {
        event.relevantPlayers.forEach(ResearchProgress::onPlayerJoin)
    }

    private fun onRightClickItemInteract(event: PlayerInteractEvent.RightClickItem) {
        val stack = event.itemStack
        if (!ResearchAccess.canAccess(event.entity, stack, ResearchAction.USE)) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.FAIL
            return
        }

        val item = stack.item
        if (item is BoundGem) {
            if (!event.entity.isShiftKeyDown) return

            event.entity.sendOverlayMessage(Component.translatable("tooltip.$ModId.${ECRModIDs.BOUND_GEM}.revoke"))
            item.setBoundPos(stack, null)
            event.cancellationResult = InteractionResult.SUCCESS
        }
    }

    private fun onRightClickBlockInteract(event: PlayerInteractEvent.RightClickBlock) {
        val stack = event.itemStack
        val level = event.level
        val pos = event.pos

        val blockAllowed = ResearchAccess.canAccess(event.entity, event.level.getBlockState(event.pos), ResearchAction.INTERACT)
        val action = if (event.itemStack.item is BlockItem) ResearchAction.PLACE else ResearchAction.USE
        val itemAllowed = ResearchAccess.canAccess(event.entity, stack, action)
        if (!blockAllowed || !itemAllowed) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.FAIL
            return
        }

        val item = stack.item
        if (item is BoundGem) {
            val device = level.resolveMRUDevice(pos)
            if (device == null || !device.deviceType.isConnectable || item.getBoundPos(stack) != null) return

            event.entity.sendOverlayMessage(
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
                    ItemEntity(level, event.entity.x, event.entity.y, event.entity.z, copied).apply {
                        this.setNoPickUpDelay()
                        this.setThrower(event.entity)
                    }

                event.level.addFreshEntity(itemEntity)
            } else {
                item.setBoundPos(stack, pos)
            }

            event.cancellationResult = InteractionResult.SUCCESS
        }
    }

    private fun onLeftClickBlock(event: PlayerInteractEvent.LeftClickBlock) {
        val blockAllowed = ResearchAccess.canAccess(event.entity, event.level.getBlockState(event.pos), ResearchAction.BREAK)
        val itemAllowed = ResearchAccess.canAccess(event.entity, event.itemStack, ResearchAction.ATTACK)
        if (!blockAllowed || !itemAllowed) event.isCanceled = true
    }

    private fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        if (!allowEntityInteraction(event)) {
            event.isCanceled = true
            event.cancellationResult = InteractionResult.FAIL
        }
    }

    private fun onAttackEntityEvent(event: AttackEntityEvent) {
        val entityAllowed = ResearchAccess.canAccess(event.entity, event.target, ResearchAction.ATTACK)
        val itemAllowed = ResearchAccess.canAccess(event.entity, event.entity.mainHandItem, ResearchAction.ATTACK)
        if (!entityAllowed || !itemAllowed) event.isCanceled = true
    }

    private fun onPlayerTick(event: PlayerTickEvent.Post) {
        (event.entity as? ServerPlayer)?.let(ResearchProgress::tick)
    }

    private fun allowEntityInteraction(event: PlayerInteractEvent): Boolean {
        val target = (event as? PlayerInteractEvent.EntityInteract)?.target ?: return true
        return ResearchAccess.canAccess(event.entity, target, ResearchAction.INTERACT) &&
            ResearchAccess.canAccess(event.entity, event.itemStack, ResearchAction.USE)
    }

    private fun onLivingDeath(e: LivingDeathEvent) {
        ECEvents.livingDeath(e.entity, e.source)
    }

    private fun extendPlatform() {
        ResearchNetwork.sendToPlayer = PacketDistributor::sendToPlayer
        ResearchNetwork.sendProgressToPlayer = PacketDistributor::sendToPlayer
        BoundGemTooltipNetwork.sendResponseToPlayer = PacketDistributor::sendToPlayer
        SoulStoneTooltipNetwork.sendResponseToPlayer = PacketDistributor::sendToPlayer
        GeoAnimationNetwork.sendToPlayer = PacketDistributor::sendToPlayer
        GeoAnimationNetwork.sendEntityToPlayer = PacketDistributor::sendToPlayer
        GeoAnimationNetwork.sendItemToPlayer = PacketDistributor::sendToPlayer
        GeoAnimationNetwork.sendBlockStopToPlayer = PacketDistributor::sendToPlayer
        GeoAnimationNetwork.sendEntityStopToPlayer = PacketDistributor::sendToPlayer
        GeoAnimationNetwork.sendItemStopToPlayer = PacketDistributor::sendToPlayer
        MagicShieldNetwork.sendToPlayer = PacketDistributor::sendToPlayer

        PlayerMatrixStorage.instance = NeoForgePlayerMatrixStorage

        countByIngredient = { (it.customIngredient as? CountIngredient)?.count ?: 1 }

        openMenuScreenInternal = { player: Player, provider: MenuProvider, _: Level, pos: BlockPos ->
            player.openMenu(provider, pos)
        }
    }
}
