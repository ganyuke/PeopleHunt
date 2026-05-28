package io.github.ganyuke.peoplehunt.core.events

typealias ReportableEventListener = (event: ReportableEvent) -> Unit
typealias MatchEventListener = (event: MatchEvent) -> Unit

class ReportableEventBus {
    private val listeners = mutableListOf<ReportableEventListener>()
    fun register(listener: ReportableEventListener) { listeners.add(listener) }
    fun unregister(listener: ReportableEventListener) { listeners.remove(listener) }
    fun post(event: ReportableEvent) { listeners.forEach { it(event) } }
}

class MatchEventBus {
    private val listeners = mutableListOf<MatchEventListener>()
    fun register(listener: MatchEventListener) { listeners.add(listener) }
    fun unregister(listener: MatchEventListener) { listeners.remove(listener) }
    fun post(event: MatchEvent) { listeners.forEach { it(event) } }
}
