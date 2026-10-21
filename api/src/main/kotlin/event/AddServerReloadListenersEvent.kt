package com.algorithmlx.ecr.api.event

import com.algorithmlx.ecr.api.event.engine.Event
import net.minecraft.resources.Identifier
import net.minecraft.server.packs.resources.PreparableReloadListener

class AddServerReloadListenersEvent(
    private val registrar: (Identifier, PreparableReloadListener) -> Unit
): Event() {
    fun addRetainedListener(id: Identifier, listener: PreparableReloadListener) = registrar(id, listener)
}
