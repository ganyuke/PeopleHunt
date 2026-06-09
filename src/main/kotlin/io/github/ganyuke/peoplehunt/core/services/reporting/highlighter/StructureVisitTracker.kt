package io.github.ganyuke.peoplehunt.core.services.reporting.highlighter

import io.github.ganyuke.peoplehunt.core.events.models.Pos4

class StructureVisitTracker {
    private data class Visited(val id: String, val pos: Pos4)
    private val visited = HashSet<Visited>()

    fun recordEntry(structureId: String, center: Pos4): Boolean =
        visited.add(Visited(structureId, center))

    fun clear() {
        visited.clear()
    }
}
