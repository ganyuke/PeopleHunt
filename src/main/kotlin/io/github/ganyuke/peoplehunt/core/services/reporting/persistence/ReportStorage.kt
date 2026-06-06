package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.FrameBatch
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.MatchOpenSession
import kotlin.time.Instant
import kotlin.uuid.Uuid

interface ReportStorage {
    suspend fun openMatch(session: MatchOpenSession)
    suspend fun appendFlush(matchId: Uuid, batch: FrameBatch, flushTime: Instant)
    suspend fun finalizeMatch(matchId: Uuid, endedAt: Instant, outcome: MatchEngine.MatchOutcome, durationTicks: Int)
    fun closeActive()
}
