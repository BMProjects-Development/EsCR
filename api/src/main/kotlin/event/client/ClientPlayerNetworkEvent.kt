package com.algorithmlx.ecr.api.event.client

import com.algorithmlx.ecr.api.event.engine.Event

sealed class ClientPlayerNetworkEvent: Event() {
    data object LoggingIn: ClientPlayerNetworkEvent()
    data object LoggingOut: ClientPlayerNetworkEvent()
    data object Clone : ClientPlayerNetworkEvent()
}
