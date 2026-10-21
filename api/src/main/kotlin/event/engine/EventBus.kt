package com.algorithmlx.ecr.api.event.engine

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass

class EventBus {
    private val sequence = AtomicLong()
    private val listeners = ConcurrentHashMap<KClass<out Event>, CopyOnWriteArrayList<RegisteredListener<*>>>()

    fun <E : Event> addListener(
        eventType: KClass<E>,
        priority: EventPriority = EventPriority.NORMAL,
        receiveCanceled: Boolean = false,
        listener: EventListener<E>
    ): Subscription {
        val registered = RegisteredListener(sequence.getAndIncrement(), priority, receiveCanceled, listener)
        listeners.computeIfAbsent(eventType) { CopyOnWriteArrayList() }.apply {
            add(registered)
            sortWith(LISTENER_ORDER)
        }
        return Subscription { listeners[eventType]?.remove(registered) }
    }

    inline fun <reified E : Event> addListener(
        priority: EventPriority = EventPriority.NORMAL,
        receiveCanceled: Boolean = false,
        noinline listener: (E) -> Unit
    ): Subscription = addListener(E::class, priority, receiveCanceled, EventListener(listener))

    inline fun <reified E : Event> subscribe(
        priority: EventPriority = EventPriority.NORMAL,
        receiveCanceled: Boolean = false,
        noinline listener: E.() -> Unit
    ): Subscription = addListener(E::class, priority, receiveCanceled) { event -> listener(event) }

    operator fun <E : Event> invoke(event: E): E = post(event)

    fun <E : Event> post(event: E): E {
        eventHierarchy(event::class)
            .flatMap { type -> listeners[type].orEmpty().asSequence() }
            .sortedWith(LISTENER_ORDER)
            .forEach { registered -> registered.dispatch(event) }
        return event
    }

    fun <E : Event> post(priority: EventPriority, event: E): E {
        eventHierarchy(event::class)
            .flatMap { type -> listeners[type].orEmpty().asSequence() }
            .filter { registered -> registered.priority == priority }
            .sortedBy(RegisteredListener<*>::sequence)
            .forEach { registered -> registered.dispatch(event) }
        return event
    }

    fun clear() = listeners.clear()

    private fun eventHierarchy(type: KClass<out Event>): Sequence<KClass<out Event>> = sequence {
        var current: Class<*>? = type.java
        while (current != null && Event::class.java.isAssignableFrom(current)) {
            @Suppress("UNCHECKED_CAST")
            yield(current.kotlin as KClass<out Event>)
            current = current.superclass
        }
    }

    private data class RegisteredListener<E : Event>(
        val sequence: Long,
        val priority: EventPriority,
        val receiveCanceled: Boolean,
        val listener: EventListener<E>
    ) {
        fun dispatch(event: Event) {
            if (event is ICancellableEvent && event.isCanceled && !receiveCanceled) return
            @Suppress("UNCHECKED_CAST")
            listener.onEvent(event as E)
        }
    }

    private companion object {
        val LISTENER_ORDER = compareBy<RegisteredListener<*>>({ it.priority.ordinal }, { it.sequence })
    }
}

object EventBuses {
    @JvmField
    val GAME = EventBus()

    @JvmField
    val MOD = EventBus()
}

inline fun EventBus.listeners(block: EventBus.() -> Unit): EventBus = apply(block)
