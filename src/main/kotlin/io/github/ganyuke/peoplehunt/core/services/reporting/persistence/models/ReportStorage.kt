package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models

import io.github.ganyuke.peoplehunt.core.services.core.models.MatchOutcome
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ReportStorage {
    val isOpen : Boolean
    suspend fun readMatch(matchId: Uuid) : PersistedMatchReport
    suspend fun openMatch(session: ReportSession)
    suspend fun appendFlush(matchId: Uuid, batch: FrameBatch, flushTime: Instant)
    suspend fun finalizeMatch(matchId: Uuid, endedAt: Instant, outcome: MatchOutcome, durationTicks: Int)
    fun closeActive()
}