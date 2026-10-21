package com.algorithmlx.ecr.api.vfx

import com.algorithmlx.ecr.api.event.client.AddClientReloadListenersEvent
import com.algorithmlx.ecr.api.event.client.ClientPlayerNetworkEvent
import com.algorithmlx.ecr.api.event.client.ClientTickEvent
import com.algorithmlx.ecr.api.event.engine.EventBuses
import com.algorithmlx.ecr.api.event.engine.listeners
import com.algorithmlx.ecr.api.utils.ecRL
import net.minecraft.client.Minecraft

object VfxClient {
    private var initialized = false

    fun init() {
        if (initialized) return
        initialized = true
        EventBuses.GAME.listeners {
            subscribe<ClientTickEvent.Post> { VfxScenes.tick() }
            subscribe<ClientPlayerNetworkEvent.LoggingOut> { VfxScenes.clear() }
        }
        EventBuses.MOD.listeners {
            subscribe<AddClientReloadListenersEvent> { addListener("vfx".ecRL, VfxAssets) }
        }
    }

    fun accept(payload: VfxCommandPayload) {
        when (payload.action) {
            VfxCommandPayload.Action.PLAY -> {
                val entity = payload.entityId.takeIf { it >= 0 }?.let { Minecraft.getInstance().level?.getEntity(it) }
                VfxScenes.play(payload.handle, payload.effect, payload.position, entity, payload.data)?.also {
                    it.attachmentId = payload.entityId.takeIf { id -> id >= 0 }
                }
            }
            VfxCommandPayload.Action.STOP -> VfxScenes.stop(payload.handle, payload.immediate)
            VfxCommandPayload.Action.MOVE -> VfxScenes.move(payload.handle, payload.position)
            VfxCommandPayload.Action.DATA -> VfxScenes.data(payload.handle, payload.data)
        }
    }
}
