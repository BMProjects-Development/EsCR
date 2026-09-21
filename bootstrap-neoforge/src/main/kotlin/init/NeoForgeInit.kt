package com.algorithmlx.ecr.neoforge.init

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.attachments.AttachmentPlatform
import com.algorithmlx.ecr.api.block.JSONBlockProperties
import com.algorithmlx.ecr.api.chunk.ChunkLoadingPlatform
import com.algorithmlx.ecr.api.config.ConfigManager
import com.algorithmlx.ecr.api.event.AddServerReloadListenersEvent as CrossAddServerReloadListenersEvent
import com.algorithmlx.ecr.api.event.BuildCreativeModeTabContentsEvent as CrossBuildCreativeModeTabContentsEvent
import com.algorithmlx.ecr.api.event.engine.EventBuses
import com.algorithmlx.ecr.api.event.OnDatapackSyncEvent as CrossOnDatapackSyncEvent
import com.algorithmlx.ecr.api.event.RegisterCommandsEvent as CrossRegisterCommandsEvent
import com.algorithmlx.ecr.api.event.entity.LivingDeathEvent as CrossLivingDeathEvent
import com.algorithmlx.ecr.api.event.entity.player.AttackEntityEvent as CrossAttackEntityEvent
import com.algorithmlx.ecr.api.event.entity.player.ItemTooltipEvent as CrossItemTooltipEvent
import com.algorithmlx.ecr.api.event.entity.player.PlayerInteractEvent as CrossPlayerInteractEvent
import com.algorithmlx.ecr.api.event.tick.PlayerTickEvent as CrossPlayerTickEvent
import com.algorithmlx.ecr.api.event.tick.ServerTickEvent as CrossServerTickEvent
import com.algorithmlx.ecr.api.event.tick.LevelTickEvent as CrossLevelTickEvent
import com.algorithmlx.ecr.api.menu.MenuTypePlatform
import com.algorithmlx.ecr.api.network.NetworkPlatform
import com.algorithmlx.ecr.api.registries.CreativeTabPlatform
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.registries.RegistrationPlatform
import com.algorithmlx.ecr.api.utils.countByIngredient
import com.algorithmlx.ecr.api.utils.openMenuScreenInternal
import com.algorithmlx.ecr.common.init.config.ECConfig
import com.algorithmlx.ecr.common.init.events.ECEventHandlers
import com.algorithmlx.ecr.common.research.ResearchConfigDisabler
import com.algorithmlx.ecr.neoforge.api.CountIngredient
import com.algorithmlx.ecr.neoforge.chunk.NeoForgeChunkLoadingPlatform
import com.algorithmlx.ecr.neoforge.init.registry.*
import com.algorithmlx.ecr.neoforge.network.NeoForgeNetworkPlatform
import com.algorithmlx.ecr.init.ECRInit
import com.algorithmlx.ecr.neoforge.utils.NeoForgePlatformUtils
import com.algorithmlx.ecr.network.ECRPackets
import com.algorithmlx.ecr.utils.PlatformUtils
import net.minecraft.core.BlockPos
import net.minecraft.world.MenuProvider
import net.minecraft.world.entity.player.Player
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
import net.neoforged.neoforge.event.tick.ServerTickEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent
import net.neoforged.neoforge.registries.NewRegistryEvent
import net.neoforged.neoforge.resource.ListenerKey
import java.io.File

object NeoForgeInit {
    fun init(bus: IEventBus) {
        RegistrationPlatform.instance = NeoForgeRegistrationPlatform(bus)
        CreativeTabPlatform.instance = NeoForgeCreativeTabPlatform
        AttachmentPlatform.instance = NeoForgeAttachmentPlatform
        MenuTypePlatform.instance = NeoForgeMenuTypePlatform
        NetworkPlatform.instance = NeoForgeNetworkPlatform(bus)
        JSONBlockProperties.allowNamespace(ModId)
        ECConfig.instance = ConfigManager.saveOrLoad(File("config/$ModId.json"), ECConfig())
        ECEventHandlers.init()

        val forgeBus = NeoForge.EVENT_BUS
        ResearchConfigDisabler.init()

        initRegistries(bus)
        ECRPackets.install()

        forgeBus.addListener(::onItemTooltip)
        forgeBus.addListener(::onAddReloadListener)
        bus.addListener(::onNewRegistry)
        bus.addListener(::onCreativeTabs)

        forgeBus.addListener(::onDatapackSync)
        forgeBus.addListener(::onRightClickItemInteract)
        forgeBus.addListener(::onRightClickBlockInteract)
        forgeBus.addListener(::onLeftClickBlock)
        forgeBus.addListener(::onEntityInteract)
        forgeBus.addListener(::onAttackEntityEvent)
        forgeBus.addListener(::onPlayerTick)
        forgeBus.addListener(::onPlayerTickPre)
        forgeBus.addListener(::onServerTickPre)
        forgeBus.addListener(::onServerTickPost)
        forgeBus.addListener(::onLevelTickPre)
        forgeBus.addListener(::onLevelTickPost)
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
        ECRInit.initRegistries()
        IngredientRegistry.init(bus)
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
        EventBuses.GAME(
            CrossItemTooltipEvent(
                event.itemStack,
                event.entity,
                event.toolTip,
                event.flags,
                event.context,
                event.display
            )
        )
    }

    private fun onCreativeTabs(event: BuildCreativeModeTabContentsEvent) {
        EventBuses.MOD(CrossBuildCreativeModeTabContentsEvent(event.tab, event::accept))
    }

    private fun onAddReloadListener(event: AddServerReloadListenersEvent) {
        EventBuses.GAME(
            CrossAddServerReloadListenersEvent { id, listener ->
                event.addRetainedListener(ListenerKey.create(id), listener)
            }
        )
    }

    private fun onRegisterCommands(event: RegisterCommandsEvent) {
        EventBuses.GAME(CrossRegisterCommandsEvent(event.dispatcher, event.buildContext, event.commandSelection))
    }

    private fun onDatapackSync(event: OnDatapackSyncEvent) {
        val crossEvent = EventBuses.GAME(CrossOnDatapackSyncEvent(event.playerList, event.player))
        event.sendRecipes(crossEvent.recipeTypesToSend)
    }

    private fun onRightClickItemInteract(event: PlayerInteractEvent.RightClickItem) {
        val crossEvent = EventBuses.GAME(CrossPlayerInteractEvent.RightClickItem(event.entity, event.hand))
        event.cancellationResult = crossEvent.cancellationResult
        event.isCanceled = crossEvent.isCanceled
    }

    private fun onRightClickBlockInteract(event: PlayerInteractEvent.RightClickBlock) {
        val crossEvent = EventBuses.GAME(CrossPlayerInteractEvent.RightClickBlock(event.entity, event.hand, event.hitVec))
        event.cancellationResult = crossEvent.cancellationResult
        event.useBlock = crossEvent.useBlock
        event.useItem = crossEvent.useItem
        event.isCanceled = crossEvent.isCanceled
    }

    private fun onLeftClickBlock(event: PlayerInteractEvent.LeftClickBlock) {
        val action = CrossPlayerInteractEvent.LeftClickBlock.Action.valueOf(event.action.name)
        val crossEvent = EventBuses.GAME(CrossPlayerInteractEvent.LeftClickBlock(event.entity, event.pos, event.face, action))
        event.useBlock = crossEvent.useBlock
        event.useItem = crossEvent.useItem
        event.isCanceled = crossEvent.isCanceled
    }

    private fun onEntityInteract(event: PlayerInteractEvent.EntityInteract) {
        val crossEvent = EventBuses.GAME(
            CrossPlayerInteractEvent.EntityInteract(event.entity, event.hand, event.target, event.location)
        )
        event.cancellationResult = crossEvent.cancellationResult
        event.isCanceled = crossEvent.isCanceled
    }

    private fun onAttackEntityEvent(event: AttackEntityEvent) {
        event.isCanceled = EventBuses.GAME(CrossAttackEntityEvent(event.entity, event.target)).isCanceled
    }

    private fun onPlayerTick(event: PlayerTickEvent.Post) {
        EventBuses.GAME(CrossPlayerTickEvent.Post(event.entity))
    }

    private fun onPlayerTickPre(event: PlayerTickEvent.Pre) {
        EventBuses.GAME(CrossPlayerTickEvent.Pre(event.entity))
    }

    private fun onServerTickPre(event: ServerTickEvent.Pre) {
        EventBuses.GAME(CrossServerTickEvent.Pre(event.server))
    }

    private fun onServerTickPost(event: ServerTickEvent.Post) {
        EventBuses.GAME(CrossServerTickEvent.Post(event.server))
    }

    private fun onLevelTickPre(event: LevelTickEvent.Pre) {
        EventBuses.GAME(CrossLevelTickEvent.Pre(event.level))
    }

    private fun onLevelTickPost(event: LevelTickEvent.Post) {
        EventBuses.GAME(CrossLevelTickEvent.Post(event.level))
    }

    private fun onLivingDeath(e: LivingDeathEvent) {
        e.isCanceled = EventBuses.GAME(CrossLivingDeathEvent(e.entity, e.source)).isCanceled
    }

    private fun extendPlatform() {
        countByIngredient = { (it.customIngredient as? CountIngredient)?.count ?: 1 }

        openMenuScreenInternal = { player: Player, provider: MenuProvider, _: Level, pos: BlockPos ->
            player.openMenu(provider, pos)
        }
    }
}
