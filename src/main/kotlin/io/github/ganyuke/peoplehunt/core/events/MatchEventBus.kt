package io.github.ganyuke.peoplehunt.core.events

class MatchEventBus {
    typealias MatchEventListener = (event: MatchEvent) -> Unit

    private val listeners = mutableListOf<MatchEventListener>()

    fun register(listener: MatchEventListener) { listeners.add(listener) }
    fun unregister(listener: MatchEventListener) { listeners.remove(listener) }
    fun post(event: MatchEvent) { listeners.forEach { it(event) } }
}
