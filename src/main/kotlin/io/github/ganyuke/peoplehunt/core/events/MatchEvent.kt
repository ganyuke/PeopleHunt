package io.github.ganyuke.peoplehunt.core.events

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.events.models.Pos4
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchState
import kotlin.uuid.Uuid

sealed class MatchEvent {
    data class MatchStart(val result: MatchState.Active) : MatchEvent()
    data class MatchEnd(val result: MatchState.Finished) : MatchEvent()
    object CompassTick : MatchEvent()
    data class GiveHuntersCompass(val huntersUuids: Set<Uuid>) : MatchEvent()
    data class CompassUpdate(val pos: Pos4, val runnerDims: Map<Uuid, Pos4>, val huntersUuids: Set<Uuid>) : MatchEvent()
    data class IntervalElapsed(val minutes: Long) : MatchEvent()
    data class BroadcastNotification(val message: String) : MatchEvent()
    data class OperatorNotification(val message: String) : MatchEvent()
    data class ReportPersisted(val matchId: Uuid) : MatchEvent()
}
