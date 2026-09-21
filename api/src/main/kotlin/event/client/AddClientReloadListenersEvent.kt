package com.algorithmlx.ecr.api.event.client

import com.algorithmlx.ecr.api.event.engine.Event
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.PreparableReloadListener

class AddClientReloadListenersEvent(private val registrar: (Identifier, PreparableReloadListener) -> Unit): Event() {
    fun addListener(id: Identifier, listener: PreparableReloadListener) = registrar(id, listener)
}
