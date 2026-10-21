package com.algorithmlx.ecr.api.event.client

import com.algorithmlx.ecr.api.event.engine.Event

sealed class ClientTickEvent: Event() {
    data object Pre: ClientTickEvent()
    data object Post: ClientTickEvent()
}
