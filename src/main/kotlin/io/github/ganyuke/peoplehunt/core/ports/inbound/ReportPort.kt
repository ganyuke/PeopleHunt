package io.github.ganyuke.peoplehunt.core.ports.inbound

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.services.reporting.CombatStatsTracker
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportOpResult
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportSessionBlockReason
import kotlin.uuid.Uuid

interface StenographerPort {
    val blockReason: ReportSessionBlockReason?

    fun flush(callback: (ReportOpResult) -> Unit)
    fun discard(): ReportOpResult
}

interface ReportEnginePort {
    val participantStats: List<Pair<MatchPlayer, CombatStatsTracker.PlayerStats>>
}

interface WebExporterPort {
    fun export(matchId: Uuid, callback: (ReportOpResult) -> Unit)
    fun fetchMatchIdList(): List<Uuid>
}

interface ReportPort : StenographerPort, ReportEnginePort, WebExporterPort