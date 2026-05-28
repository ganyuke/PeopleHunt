package io.github.ganyuke.peoplehunt.core.services

import kotlin.uuid.Uuid
import io.github.ganyuke.peoplehunt.core.Utils.Pos4
import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent

class CompassService(private val outbound: MatchEventBus) {

    private var runnerUuid: Uuid? = null
    private var runnerDim: Uuid? = null
    private val runnerPosInDim: MutableMap<Uuid, Pos4> = mutableMapOf()

    fun onReportableEvent(event: ReportableEvent) {
        if (event is ReportableEvent.PlayerMoved && event.player == runnerUuid) {
            runnerDim = event.pos.w
            runnerPosInDim[event.pos.w] = event.pos
        }
    }

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.MatchStart -> {
                runnerUuid = event.runner
                runnerPosInDim.clear()
                runnerDim = null
            }
            is MatchEvent.CompassTick -> tick()
            is MatchEvent.MatchEnd -> {
                runnerUuid = null
                runnerDim = null
                runnerPosInDim.clear()
            }
            else -> {}
        }
    }

    private fun tick() {
        val dim = runnerDim ?: return
        val pos = runnerPosInDim[dim] ?: return
        val runner = runnerUuid ?: return
        outbound.post(MatchEvent.CompassUpdate(dim, pos, runner))
    }
}
