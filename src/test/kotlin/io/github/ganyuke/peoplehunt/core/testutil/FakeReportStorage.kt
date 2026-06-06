package io.github.ganyuke.peoplehunt.core.testutil

import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportStorage
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.FrameBatch
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.MatchOpenSession
import kotlin.time.Instant
import kotlin.uuid.Uuid

class FakeReportStorage : ReportStorage {
    data class AppendCall(val matchId: Uuid, val batch: FrameBatch, val flushTime: Instant)

    val openMatchCalls = mutableListOf<MatchOpenSession>()
    val appendCalls = mutableListOf<AppendCall>()
    val finalizeCalls = mutableListOf<FinalizeCall>()
    var closeActiveCalls = 0

    var failOpen = false
    var failAppend = false
    var failFinalize = false

    data class FinalizeCall(
        val matchId: Uuid,
        val endedAt: Instant,
        val outcome: MatchEngine.MatchOutcome,
        val durationTicks: Int,
    )

    override suspend fun openMatch(session: MatchOpenSession) {
        if (failOpen) error("openMatch failed")
        openMatchCalls += session
    }

    override suspend fun appendFlush(matchId: Uuid, batch: FrameBatch, flushTime: Instant) {
        if (failAppend) error("appendFlush failed")
        appendCalls += AppendCall(matchId, batch, flushTime)
    }

    override suspend fun finalizeMatch(
        matchId: Uuid,
        endedAt: Instant,
        outcome: MatchEngine.MatchOutcome,
        durationTicks: Int,
    ) {
        if (failFinalize) error("finalizeMatch failed")
        finalizeCalls += FinalizeCall(matchId, endedAt, outcome, durationTicks)
    }

    override fun closeActive() {
        closeActiveCalls++
    }
}
