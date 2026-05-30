package io.github.ganyuke.peoplehunt.core.events

class ReportableEventBus {
    typealias ReportableEventListener = (event: ReportableEvent) -> Unit

    private val listeners = mutableListOf<ReportableEventListener>()

    fun register(listener: ReportableEventListener) { listeners.add(listener) }
    fun unregister(listener: ReportableEventListener) { listeners.remove(listener) }
    fun post(event: ReportableEvent) { listeners.forEach { it(event) } }
}