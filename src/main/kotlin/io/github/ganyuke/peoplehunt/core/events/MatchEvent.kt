package io.github.ganyuke.peoplehunt.core.events

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.events.models.Pos4
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import kotlin.uuid.Uuid

sealed class MatchEvent {
    data class MatchStart(val runner: MatchPlayer, val hunters: Set<MatchPlayer>) : MatchEvent()
    data class MatchEnd(val result: MatchEngine.MatchState.Finished) : MatchEvent()
    object CompassTick : MatchEvent()
    data class GiveHuntersCompass(val huntersUuids: Set<Uuid>) : MatchEvent()
    data class CompassUpdate(val pos: Pos4, val runnerDims: Map<Uuid, Pos4>, val huntersUuids: Set<Uuid>) : MatchEvent()
    data class IntervalElapsed(val minutes: Long) : MatchEvent()
    data class BroadcastNotification(val message: String) : MatchEvent()
    data class OperatorNotification(val message: String) : MatchEvent()
    data class ReportPersisted(val matchId: Uuid) : MatchEvent()
}
