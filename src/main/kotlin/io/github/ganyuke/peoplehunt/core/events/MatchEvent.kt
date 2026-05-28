package io.github.ganyuke.peoplehunt.core.events

import kotlin.uuid.Uuid
import io.github.ganyuke.peoplehunt.core.Utils.Pos4
import io.github.ganyuke.peoplehunt.core.services.MatchEngine

sealed class MatchEvent {
    data class MatchStart(val runner: MatchEngine.MatchPlayer) : MatchEvent()
    data class MatchEnd(val reason: MatchEngine.MatchEndReason) : MatchEvent()
    object CompassTick : MatchEvent()
    data class CompassUpdate(val dim: Uuid, val pos: Pos4, val runner: MatchEngine.MatchPlayer) : MatchEvent()
    data class IntervalElapsed(val elapsedSeconds: Long) : MatchEvent()
    data class BroadcastNotification(val message: String) : MatchEvent()
    data class OperatorNotification(val message: String) : MatchEvent()
}
