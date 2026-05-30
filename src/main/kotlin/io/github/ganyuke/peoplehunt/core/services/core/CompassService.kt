package io.github.ganyuke.peoplehunt.core.services.core

import kotlin.uuid.Uuid
import io.github.ganyuke.peoplehunt.core.Utils.Pos4
import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent

class CompassService(private val outbound: MatchEventBus) {
    private var runnerUuid: Uuid? = null
    private var runnerDim: Uuid? = null
    private var huntersUuid: Set<Uuid> = emptySet()
    private val runnerPosInDim: MutableMap<Uuid, Pos4> = mutableMapOf()

    fun onReportableEvent(event: ReportableEvent) : Unit =
        when (event) {
            // feature: update tracked position on runner movement in dimension
            is ReportableEvent.PlayerMoved -> {
                if (event.player.uuid != runnerUuid) return

                runnerDim = event.pos.w
                runnerPosInDim[event.pos.w] = event.pos
            }

            // feature: give compass on hunter respawn
            is ReportableEvent.PlayerRespawned -> {
                if (event.player.uuid !in huntersUuid) return
                outbound.post(MatchEvent.GiveHuntersCompass(setOf(event.player.uuid)))
            }

            else -> {}
        }

    fun onMatchEvent(event: MatchEvent) =
        when (event) {
            is MatchEvent.MatchStart -> {
                runnerUuid = event.runner.uuid
                huntersUuid = event.hunters.map { it.uuid }.toSet()
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

    private fun tick() {
        val dim = runnerDim ?: return
        val pos = runnerPosInDim[dim] ?: return
        outbound.post(MatchEvent.CompassUpdate(pos, runnerPosInDim, huntersUuid))
    }
}
