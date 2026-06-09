package io.github.ganyuke.peoplehunt.core.ports.inbound

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.services.reporting.highlighter.CombatStatsTracker
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportOpResult
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.stenography.ReportStartRejectReason
import java.nio.file.Path
import kotlin.uuid.Uuid

interface StenographerPort {
    val blockReason: ReportStartRejectReason?

    fun flush(callback: (ReportOpResult) -> Unit)
    fun discard(): ReportOpResult
}

interface ReportEnginePort {
    val participantStats: List<Pair<MatchPlayer, CombatStatsTracker.PlayerStats>>
}

interface WebExporterPort {
    fun export(matchId: Uuid, callback: (ReportOpResult, Path?) -> Unit)
    fun fetchMatchIdList(): List<Uuid>
}

interface ReportPort : StenographerPort, ReportEnginePort, WebExporterPort