package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.ports.inbound.StenographerPort
import io.github.ganyuke.peoplehunt.core.ports.outbound.LoggerPort
import io.github.ganyuke.peoplehunt.core.ports.outbound.TaskHandle
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchState
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.FinalizedMetadata
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class ReportStenographer(
    val logger: LoggerPort
) : StenographerPort {
    sealed interface ReportState {
        val session: ReportSession

        // init state, finished final write state
        object Closed : ReportState {
            override val session: ReportSession = error("tried to access session for closed session")
        }

        // match start state, creating DB state
        data class Initializing(
            override val session: ReportSession
        ) : ReportState

        // match start state, open DB failed, allow manual flush
        data class InitializedFailed(
            override val session: ReportSession
        ) : ReportState

        // successfully opened DB state, primary state
        data class Active(
            override val session: ReportSession
        ) : ReportState

        // match end state, awaiting final flush to disk
        data class Finalizing(
            override val session: ReportSession,
            val metadata: FinalizedMetadata
        ) : ReportState

        // match end state, failed final flush to disk, allow manual flush
        data class FinalizeFailed(
            override val session: ReportSession,
            val metadata: FinalizedMetadata
        ) : ReportState
    }

    var reportState: ReportState = ReportState.Closed

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // todo: create task handler for flush task to handle ongoing flush operations

    // todo: create task handler for interval task to handle cancelling/no-op interval operation

    override val blockReason: ReportSessionBlockReason? get() = when (reportState) {
        ReportState.Closed -> null
        is ReportState.Active, is ReportState.Initializing, is ReportState.InitializedFailed -> ReportSessionBlockReason.SESSION_ALREADY_ACTIVE
        is ReportState.Finalizing, is ReportState.FinalizeFailed -> ReportSessionBlockReason.FINALIZE_PENDING
    }

    override fun flush(callback: (ReportOpResult) -> Unit) {
        // callback handles how feedback is done
        // todo: implement flush interval task that calls this w/ operator and logger feedback
        // todo: implement manual flush task that calls this w/ sender and logger feedback
        // todo: implement match end flush task that calls this w/ operator and logger feedback

        /* todo: manual flush has these branches:
        *   (1) if reportState is Closed, report error: no data to flush
        *   (2) if reportState is InitializedFailed: attempt to create DB then flush stored data
        *   (3) if reportState is FinalizationFailed: re-attempt finalized flush to DB
        *   (4) if reportState is Active:
        *       (a) if DB flush already in progress: reject and notify to try again
        *       (b) if DB flush not in progress: flush current data to DB
        *   (5) if reportState is either Initializing or Finalizing: block writes; report is doing DB work
        */

        // todo: autoflush will be skipped if the interval hits while it is already flushing data to DB
    }

    override fun discard(): ReportOpResult {
        reportState = ReportState.Closed
        return ReportOpResult.Ok("Discarded reporting session. Match must be (re)started to resume reporting.")
    }

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> openSession(event)
            is MatchEvent.MatchEnd -> closeSession(event)
            else -> {}
        }
    }

    fun onReportableEvent(event: ReportableEvent) {
        val state = reportState
        if (state is ReportState.Closed ||
            state is ReportState.Finalizing ||
            state is ReportState.FinalizeFailed
        ) return

        val newSession = state.session.withEvent(event)
        val newState = when (state) {
            is ReportState.Initializing -> state.copy(session = newSession)
            is ReportState.InitializedFailed -> state.copy(session = newSession)
            is ReportState.Active -> state.copy(session = newSession)
        }

        reportState = newState
    }

    private fun openSession(event: MatchEvent.MatchStart) {
        val match = event.result
        reportState = ReportState.Initializing(
            ReportSession(
                startedAt = match.startedAt,
                runner = match.runner,
                hunters = match.hunters.toList(),
            )
        )
    }

    private fun buildFinalize(match: MatchState.Finished, session: ReportSession) = ReportState.Finalizing(
        endedAt = match.endedAt,
        outcome = match.outcome,
        durationTicks = session.latestTick,
        session = session
    )

    private fun closeSession(event: MatchEvent.MatchEnd) {
        val match = event.result
        val newState = when (val state = reportState) {
            is ReportState.Active -> buildFinalize(match, state.session)
            is ReportState.Initializing -> buildFinalize(match, state.session)
            is ReportState.InitializedFailed -> buildFinalize(match, state.session)

            is ReportState.Finalizing, is ReportState.FinalizeFailed -> {
                logger.warn("Closed reporting session while finalizing write to disk! Data was lost!")
                ReportState.Closed
            }

            else -> ReportState.Closed
        }

        reportState = newState
    }

}