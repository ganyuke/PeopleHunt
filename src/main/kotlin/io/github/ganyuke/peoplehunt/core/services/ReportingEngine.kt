package io.github.ganyuke.peoplehunt.core.services

class ReportingEngine {
    data class ParticipantStats(val player: MatchEngine.MatchPlayer, val kills: Int, val deaths: Int)

    fun getParticipantStats(): List<ParticipantStats> {
        return emptyList()
    }
}