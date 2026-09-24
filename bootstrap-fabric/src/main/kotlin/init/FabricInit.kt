package com.algorithmlx.ecr.fabric.init

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.attachments.AttachmentPlatform
import com.algorithmlx.ecr.api.block.JSONBlockProperties
import com.algorithmlx.ecr.api.chunk.ChunkLoadingPlatform
import com.algorithmlx.ecr.api.config.ConfigManager
import com.algorithmlx.ecr.api.event.AddServerReloadListenersEvent
import com.algorithmlx.ecr.api.event.BuildCreativeModeTabContentsEvent
import com.algorithmlx.ecr.api.event.OnDatapackSyncEvent
import com.algorithmlx.ecr.api.event.RegisterCommandsEvent
import com.algorithmlx.ecr.api.event.engine.EventBuses
import com.algorithmlx.ecr.api.event.entity.LivingDeathEvent
import com.algorithmlx.ecr.api.event.entity.player.AttackEntityEvent
import com.algorithmlx.ecr.api.event.entity.player.PlayerInteractEvent
import com.algorithmlx.ecr.api.event.tick.LevelTickEvent
import com.algorithmlx.ecr.api.event.tick.PlayerTickEvent
import com.algorithmlx.ecr.api.event.tick.ServerTickEvent
import com.algorithmlx.ecr.api.menu.MenuTypeData
import com.algorithmlx.ecr.api.menu.MenuTypePlatform
import com.algorithmlx.ecr.api.network.NetworkPlatform
import com.algorithmlx.ecr.api.registries.CreativeTabPlatform
import com.algorithmlx.ecr.api.registries.ECRegistries
import com.algorithmlx.ecr.api.registries.ECRegistryKeys
import com.algorithmlx.ecr.api.registries.RegistrationPlatform
import com.algorithmlx.ecr.api.utils.countByIngredient
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.api.utils.openMenuScreenInternal
import com.algorithmlx.ecr.init.ECRModIDs
import com.algorithmlx.ecr.init.config.ECConfig
import com.algorithmlx.ecr.init.events.ECEventHandlers
import com.algorithmlx.ecr.common.research.ResearchConfigDisabler
import com.algorithmlx.ecr.fabric.api.CountIngredient
import com.algorithmlx.ecr.fabric.chunk.FabricChunkLoadingPlatform
import com.algorithmlx.ecr.fabric.init.registry.FabricAttachmentPlatform
import com.algorithmlx.ecr.fabric.init.registry.FabricCreativeTabPlatform
import com.algorithmlx.ecr.fabric.init.registry.FabricMenuTypePlatform
import com.algorithmlx.ecr.fabric.init.registry.FabricRegistrationPlatform
import com.algorithmlx.ecr.fabric.network.FabricNetworkPlatform
import com.algorithmlx.ecr.init.ECRInit
import com.algorithmlx.ecr.fabric.utils.FabricPlatformUtils
import com.algorithmlx.ecr.network.ECRPackets
import com.algorithmlx.ecr.utils.PlatformUtils
import net.fabricmc.fabric.api.biome.v1.BiomeModifications
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.event.player.*
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider
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
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu
import net.minecraft.world.level.levelgen.GenerationStep
import java.io.File

object FabricInit {
    @JvmStatic
    fun init() {
        RegistrationPlatform.instance = FabricRegistrationPlatform
        CreativeTabPlatform.instance = FabricCreativeTabPlatform
        AttachmentPlatform.instance = FabricAttachmentPlatform
        MenuTypePlatform.instance = FabricMenuTypePlatform
        NetworkPlatform.instance = FabricNetworkPlatform
        JSONBlockProperties.allowNamespace(ModId)
        ECConfig.instance = ConfigManager.saveOrLoad(File("config/$ModId.json"), ECConfig())
        ECEventHandlers.init()

        initBuiltinRegistries()
        ResearchConfigDisabler.init()

        ECRPackets.install()
        registerReloadListener()
        registerProgressEvents()
        registerAccessEvents()
        registerTabEvent()
        registerEntityEvents()

        initRegistries()
        registerWorldgen()

        CommandRegistrationCallback.EVENT.register { dispatcher, context, selection ->
            EventBuses.GAME(RegisterCommandsEvent(dispatcher, context, selection))
        }

        extendPlatform()
    }

    private fun initRegistries() {
        PlatformUtils.instance = FabricPlatformUtils
        ChunkLoadingPlatform.instance = FabricChunkLoadingPlatform
        ECRInit.initRegistries()

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

    private fun registerReloadListener() {
        val loader = ResourceLoader.get(PackType.SERVER_DATA)
        EventBuses.GAME(AddServerReloadListenersEvent(loader::registerReloadListener))
    }

    private fun registerWorldgen() {
        BiomeModifications.addFeature(
            BiomeSelectors.foundInOverworld(),
            GenerationStep.Decoration.UNDERGROUND_ORES,
            ResourceKey.create(Registries.PLACED_FEATURE, ECRModIDs.MITHRILINE_ORE.ecRL)
        )
    }

    private fun registerProgressEvents() {
        ServerLifecycleEvents.SYNC_DATA_PACK_CONTENTS.register { player, _ ->
            EventBuses.GAME(OnDatapackSyncEvent(player.level().server.playerList, player))
        }
        ServerLifecycleEvents.END_DATA_PACK_RELOAD.register { server, _, success ->
            if (success) EventBuses.GAME(OnDatapackSyncEvent(server.playerList, null))
        }
        ServerTickEvents.START_SERVER_TICK.register { server ->
            EventBuses.GAME(ServerTickEvent.Pre(server))
            server.playerList.players.forEach { player -> EventBuses.GAME(PlayerTickEvent.Pre(player)) }
        }
        ServerTickEvents.END_SERVER_TICK.register { server ->
            server.playerList.players.forEach { player -> EventBuses.GAME(PlayerTickEvent.Post(player)) }
            EventBuses.GAME(ServerTickEvent.Post(server))
        }
        ServerTickEvents.START_LEVEL_TICK.register { level -> EventBuses.GAME(LevelTickEvent.Pre(level)) }
        ServerTickEvents.END_LEVEL_TICK.register { level -> EventBuses.GAME(LevelTickEvent.Post(level)) }
    }

    private fun registerAccessEvents() {
        UseItemCallback.EVENT.register { player, _, hand ->
            EventBuses.GAME(PlayerInteractEvent.RightClickItem(player, hand)).result()
        }
        UseBlockCallback.EVENT.register { player, level, hand, hit ->
            EventBuses.GAME(PlayerInteractEvent.RightClickBlock(player, hand, hit)).result()
        }
        AttackBlockCallback.EVENT.register { player, _, _, pos, direction ->
            val event = PlayerInteractEvent.LeftClickBlock(
                player,
                pos,
                direction,
                PlayerInteractEvent.LeftClickBlock.Action.START
            )
            if (EventBuses.GAME(event).isCanceled) InteractionResult.FAIL else InteractionResult.PASS
        }
        UseEntityCallback.EVENT.register { player, _, hand, entity, hit ->
            val location = hit.location.subtract(entity.position())
            EventBuses.GAME(PlayerInteractEvent.EntityInteract(player, hand, entity, location)).result()
        }
        AttackEntityCallback.EVENT.register { player, _, _, entity, _ ->
            if (EventBuses.GAME(AttackEntityEvent(player, entity)).isCanceled) InteractionResult.FAIL else InteractionResult.PASS
        }
    }

    private fun registerTabEvent() {
        CreativeModeTabEvents.MODIFY_OUTPUT_ALL.register { tab, output ->
            EventBuses.MOD(BuildCreativeModeTabContentsEvent(tab, output::accept))
        }
    }

    private fun registerEntityEvents() {
        ServerLivingEntityEvents.ALLOW_DEATH.register { entity, source, _ ->
            !EventBuses.GAME(LivingDeathEvent(entity, source)).isCanceled
        }
    }

    private fun extendPlatform() {
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
                        player: Player
                    ): AbstractContainerMenu? = provider.createMenu(containerId, inventory, player)

                    override fun getScreenOpeningData(player: ServerPlayer): MenuTypeData = MenuTypeData(pos)
                }
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T : Registry<*>> register(
        resourceKey: ResourceKey<T>,
        t: T
    ): T = Registry.register(BuiltInRegistries.REGISTRY as Registry<Registry<*>>, resourceKey.identifier(), t)

    private fun PlayerInteractEvent.RightClickItem.result(): InteractionResult =
        if (isCanceled) cancellationResult else InteractionResult.PASS

    private fun PlayerInteractEvent.RightClickBlock.result(): InteractionResult =
        if (isCanceled) cancellationResult else InteractionResult.PASS

    private fun PlayerInteractEvent.EntityInteract.result(): InteractionResult =
        if (isCanceled) cancellationResult else InteractionResult.PASS
}
