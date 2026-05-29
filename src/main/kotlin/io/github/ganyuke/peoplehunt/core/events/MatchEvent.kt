package io.github.ganyuke.peoplehunt.core.events

import kotlin.uuid.Uuid
import io.github.ganyuke.peoplehunt.core.Utils.Pos4
import io.github.ganyuke.peoplehunt.core.services.MatchEngine

sealed class MatchEvent {
    data class MatchStart(val runner: MatchEngine.MatchPlayer, val hunters: Set<MatchEngine.MatchPlayer>) : MatchEvent()
    data class MatchEnd(val reason: MatchEngine.MatchOutcome) : MatchEvent()
    object CompassTick : MatchEvent()
    data class GiveHuntersCompass(val huntersUuids: Set<Uuid>) : MatchEvent()
    data class CompassUpdate(val pos: Pos4, val runnerDims: Map<Uuid, Pos4>, val huntersUuids: Set<Uuid>) : MatchEvent()
    data class IntervalElapsed(val elapsedSeconds: Long) : MatchEvent()
    data class BroadcastNotification(val message: String) : MatchEvent()
    data class OperatorNotification(val message: String) : MatchEvent()
}
