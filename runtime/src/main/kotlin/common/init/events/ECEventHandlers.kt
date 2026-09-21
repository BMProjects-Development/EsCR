package com.algorithmlx.ecr.common.init.events

import com.algorithmlx.ecr.api.ModId
import com.algorithmlx.ecr.api.config.ConfigManager
import com.algorithmlx.ecr.api.event.AddServerReloadListenersEvent
import com.algorithmlx.ecr.api.event.BuildCreativeModeTabContentsEvent
import com.algorithmlx.ecr.api.event.engine.EventBuses
import com.algorithmlx.ecr.api.event.OnDatapackSyncEvent
import com.algorithmlx.ecr.api.event.RegisterCommandsEvent
import com.algorithmlx.ecr.api.event.entity.LivingDeathEvent
import com.algorithmlx.ecr.api.event.entity.player.AttackEntityEvent
import com.algorithmlx.ecr.api.event.entity.player.ItemTooltipEvent
import com.algorithmlx.ecr.api.event.entity.player.PlayerInteractEvent
import com.algorithmlx.ecr.api.event.engine.listeners
import com.algorithmlx.ecr.api.event.tick.PlayerTickEvent
import com.algorithmlx.ecr.api.item.BoundGem
import com.algorithmlx.ecr.api.item.HasSubItem
import com.algorithmlx.ecr.api.item.NoTab
import com.algorithmlx.ecr.api.mru.resolveMRUDevice
import com.algorithmlx.ecr.api.multiblock.MultiblockDataReloadListener
import com.algorithmlx.ecr.api.research.ResearchAccess
import com.algorithmlx.ecr.api.research.ResearchProgress
import com.algorithmlx.ecr.api.research.content.ResearchAction
import com.algorithmlx.ecr.api.utils.ecRL
import com.algorithmlx.ecr.common.init.ECRCommands
import com.algorithmlx.ecr.common.init.ECRModIDs
import com.algorithmlx.ecr.common.init.reload.ResearchReloadListener
import com.algorithmlx.ecr.common.init.reload.SoulStoneDataReloadListener
import com.algorithmlx.ecr.registry.CreativeTabRegistry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.item.ItemEntity
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.ItemStack

object ECEventHandlers {
    private var initialized = false

    fun init() {
        check(!initialized) { "Event handlers are already initialized" }
        initialized = true

        EventBuses.GAME.listeners {
            subscribe<ItemTooltipEvent> { ECEvents.itemTooltip(itemStack, toolTip) }
            subscribe<LivingDeathEvent> { ECEvents.livingDeath(entity, source) }
            subscribe<PlayerInteractEvent.RightClickItem> { handle() }
            subscribe<PlayerInteractEvent.RightClickBlock> { handle() }
            subscribe<PlayerInteractEvent.LeftClickBlock> { handle() }
            subscribe<PlayerInteractEvent.EntityInteract> { handle() }
            subscribe<AttackEntityEvent> { handle() }
            subscribe<PlayerTickEvent.Post> { (entity as? ServerPlayer)?.let(ResearchProgress::tick) }
            subscribe<RegisterCommandsEvent> { ECRCommands.register(dispatcher) }
            subscribe<OnDatapackSyncEvent> { relevantPlayers.forEach(ResearchProgress::onPlayerJoin) }
            subscribe<AddServerReloadListenersEvent> { registerListeners() }
        }

        EventBuses.MOD.subscribe<BuildCreativeModeTabContentsEvent> { buildContents() }
    }

    private fun PlayerInteractEvent.RightClickItem.handle() {
        if (!ResearchAccess.canAccess(entity, itemStack, ResearchAction.USE)) {
            isCanceled = true
            cancellationResult = InteractionResult.FAIL
            return
        }

        val item = itemStack.item as? BoundGem ?: return
        if (!entity.isShiftKeyDown || item.getBoundPos(itemStack) == null) return

        entity.sendOverlayMessage(Component.translatable("tooltip.$ModId.${ECRModIDs.BOUND_GEM}.revoke"))
        item.setBoundPos(itemStack, null)
        cancellationResult = InteractionResult.SUCCESS
        isCanceled = true
    }

    private fun PlayerInteractEvent.RightClickBlock.handle() {
        val blockAllowed = ResearchAccess.canAccess(entity, level.getBlockState(pos), ResearchAction.INTERACT)
        val action = if (itemStack.item is BlockItem) ResearchAction.PLACE else ResearchAction.USE
        val itemAllowed = ResearchAccess.canAccess(entity, itemStack, action)
        if (!blockAllowed || !itemAllowed) {
            isCanceled = true
            cancellationResult = InteractionResult.FAIL
            return
        }

        val item = itemStack.item as? BoundGem ?: return
        val device = level.resolveMRUDevice(pos)
        if (device == null || !device.deviceType.isConnectable || item.getBoundPos(itemStack) != null) return

        entity.sendOverlayMessage(
            Component.translatable("tooltip.$ModId.${ECRModIDs.BOUND_GEM}.linked")
                .append(": X: ${pos.x} Y: ${pos.y} Z: ${pos.z}")
        )

        if (itemStack.count > 1) {
            val copy = itemStack.copy().apply {
                count = 1
                item.setBoundPos(this, pos)
            }
            itemStack.shrink(1)
            level.addFreshEntity(ItemEntity(level, entity.x, entity.y, entity.z, copy).apply {
                setNoPickUpDelay()
                setThrower(entity)
            })
        } else {
            item.setBoundPos(itemStack, pos)
        }

        cancellationResult = InteractionResult.SUCCESS
        isCanceled = true
    }

    private fun PlayerInteractEvent.LeftClickBlock.handle() {
        val blockAllowed = ResearchAccess.canAccess(entity, level.getBlockState(pos), ResearchAction.BREAK)
        val itemAllowed = ResearchAccess.canAccess(entity, itemStack, ResearchAction.ATTACK)
        if (!blockAllowed || !itemAllowed) isCanceled = true
    }

    private fun PlayerInteractEvent.EntityInteract.handle() {
        val entityAllowed = ResearchAccess.canAccess(entity, target, ResearchAction.INTERACT)
        val itemAllowed = ResearchAccess.canAccess(entity, itemStack, ResearchAction.USE)
        if (!entityAllowed || !itemAllowed) {
            isCanceled = true
            cancellationResult = InteractionResult.FAIL
        }
    }

    private fun AttackEntityEvent.handle() {
        val entityAllowed = ResearchAccess.canAccess(entity, target, ResearchAction.ATTACK)
        val itemAllowed = ResearchAccess.canAccess(entity, entity.mainHandItem, ResearchAction.ATTACK)
        if (!entityAllowed || !itemAllowed) isCanceled = true
    }

    private fun AddServerReloadListenersEvent.registerListeners() {
        addRetainedListener("multiblocks".ecRL, MultiblockDataReloadListener())
        addRetainedListener("research".ecRL, ResearchReloadListener())
        addRetainedListener("settings/${ECRModIDs.SOUL_STONE}".ecRL, SoulStoneDataReloadListener(ConfigManager.json))
    }

    private fun BuildCreativeModeTabContentsEvent.buildContents() {
        BuiltInRegistries.ITEM.keySet().asSequence()
            .filter { id -> id.namespace == ModId }
            .mapNotNull { id -> BuiltInRegistries.ITEM.getOptional(id).orElse(null) }
            .forEach { item ->
                when {
                    tab == CreativeTabRegistry.blocks.get() &&
                        item is BlockItem &&
                        item.block !is NoTab -> accept(item)
                    BuiltInRegistries.BLOCK.getOptional(BuiltInRegistries.ITEM.getKey(item)).isPresent -> Unit
                    item is NoTab || tab != CreativeTabRegistry.items.get() -> Unit
                    item is HasSubItem -> item.addSubItems(ItemStack(item)).forEach(::accept)
                    else -> accept(item)
                }
            }
    }
}
