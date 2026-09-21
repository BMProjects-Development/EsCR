package com.algorithmlx.ecr.api.event.engine

abstract class Event protected constructor() {
    internal var cancellationState = false
}

interface ICancellableEvent {
    var isCanceled: Boolean
        get() = (this as Event).cancellationState
        set(value) {
            (this as Event).cancellationState = value
        }
}

enum class EventPriority {
    HIGHEST,
    HIGH,
    NORMAL,
    LOW,
    LOWEST
}

fun interface EventListener<in E : Event> {
    fun onEvent(event: E)
}

@JvmInline
value class Subscription internal constructor(internal val remove: () -> Unit) : AutoCloseable {
    override fun close() = remove()
}
