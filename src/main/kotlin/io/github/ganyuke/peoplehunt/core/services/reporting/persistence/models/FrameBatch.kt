package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models

data class FrameBatch(
    val projectiles: List<EventFrame>,
    val snapshots: List<EventFrame>,
    val events: List<EventFrame>,
) {
    fun isEmpty(): Boolean = projectiles.isEmpty() && snapshots.isEmpty() && events.isEmpty()

    fun tickRange(): IntRange? {
        val ticks = buildList {
            projectiles.forEach { add(it.tick) }
            snapshots.forEach { add(it.tick) }
            events.forEach { add(it.tick) }
        }
        if (ticks.isEmpty()) return null
        return ticks.min()..ticks.max()
    }
}
