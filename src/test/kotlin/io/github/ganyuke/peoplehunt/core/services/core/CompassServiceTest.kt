package io.github.ganyuke.peoplehunt.core.services.core

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchOutcome
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchState
import io.github.ganyuke.peoplehunt.core.testutil.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid

class CompassServiceTest {
    @Test
    fun matchStart_setsRunnerAndHunters() {
        val bus = MatchEventBus()
        val events = mutableListOf<MatchEvent>()
        bus.register { events += it }
        val service = CompassService(bus)
        val runner = player("runner")
        val hunter = player("hunter")
        service.onMatchEvent(MatchEvent.MatchStart(runner, setOf(hunter)))
        service.onReportableEvent(playerMoved(runner, pos(1, 2, 3, Uuid.random())))
        service.onMatchEvent(MatchEvent.CompassTick)
        assertTrue(events.any { it is MatchEvent.CompassUpdate })
    }

    @Test
    fun ignoresNonRunnerMovement() {
        val bus = MatchEventBus()
        val events = mutableListOf<MatchEvent>()
        bus.register { events += it }
        val service = CompassService(bus)
        val runner = player("runner")
        service.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        service.onReportableEvent(playerMoved(player("other")))
        service.onMatchEvent(MatchEvent.CompassTick)
        assertTrue(events.none { it is MatchEvent.CompassUpdate })
    }

    @Test
    fun hunterRespawn_givesCompass() {
        val bus = MatchEventBus()
        val events = mutableListOf<MatchEvent>()
        bus.register { events += it }
        val service = CompassService(bus)
        val runner = player("runner")
        val hunter = player("hunter")
        service.onMatchEvent(MatchEvent.MatchStart(runner, setOf(hunter)))
        service.onReportableEvent(playerRespawned(hunter))
        val give = events.filterIsInstance<MatchEvent.GiveHuntersCompass>().last()
        assertEquals(setOf(hunter.uuid), give.huntersUuids)
    }

    @Test
    fun nonHunterRespawn_isIgnored() {
        val bus = MatchEventBus()
        val events = mutableListOf<MatchEvent>()
        bus.register { events += it }
        val service = CompassService(bus)
        val runner = player("runner")
        service.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        service.onReportableEvent(playerRespawned(player("stranger")))
        assertTrue(events.none { it is MatchEvent.GiveHuntersCompass })
    }

    @Test
    fun matchEnd_clearsTracking() {
        val bus = MatchEventBus()
        val events = mutableListOf<MatchEvent>()
        bus.register { events += it }
        val service = CompassService(bus)
        val runner = player("runner")
        val world = Uuid.random()
        service.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        service.onReportableEvent(playerMoved(runner, pos(0, 0, 0, world)))
        service.onMatchEvent(MatchEvent.MatchEnd(
            MatchState.Finished(
                runner,
                emptySet(),
                Clock.System.now(),
                Clock.System.now(),
                MatchOutcome.INCONCLUSIVE,
            ),
        ))
        service.onMatchEvent(MatchEvent.CompassTick)
        assertTrue(events.none { it is MatchEvent.CompassUpdate })
    }

    @Test
    fun compassTick_withoutPosition_doesNotUpdate() {
        val bus = MatchEventBus()
        val events = mutableListOf<MatchEvent>()
        bus.register { events += it }
        val service = CompassService(bus)
        val runner = player("runner")
        service.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        service.onMatchEvent(MatchEvent.CompassTick)
        assertTrue(events.none { it is MatchEvent.CompassUpdate })
    }

    @Test
    fun unrelatedReportableEvents_areIgnored() {
        val bus = MatchEventBus()
        val service = CompassService(bus)
        service.onReportableEvent(
            playerDamagedEntity(player("p"), "minecraft:zombie", 1.0),
        )
    }

    @Test
    fun unrelatedMatchEvents_areIgnored() {
        val bus = MatchEventBus()
        val service = CompassService(bus)
        service.onMatchEvent(MatchEvent.BroadcastNotification("hi"))
    }
}
