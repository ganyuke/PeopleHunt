package io.github.ganyuke.peoplehunt.core.services.core

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.pos
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
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
        service.onReportableEvent(ReportableEvent.PlayerMoved(runner, pos(1, 2, 3, Uuid.random())))
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
        service.onReportableEvent(ReportableEvent.PlayerMoved(player("other"), pos()))
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
        service.onReportableEvent(ReportableEvent.PlayerRespawned(hunter, pos()))
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
        service.onReportableEvent(ReportableEvent.PlayerRespawned(player("stranger"), pos()))
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
        service.onReportableEvent(ReportableEvent.PlayerMoved(runner, pos(0, 0, 0, world)))
        service.onMatchEvent(MatchEvent.MatchEnd(
            MatchEngine.MatchState.Finished(
                runner,
                emptySet(),
                kotlin.time.Clock.System.now(),
                kotlin.time.Clock.System.now(),
                MatchEngine.MatchOutcome.INCONCLUSIVE,
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
            ReportableEvent.PlayerDamagedEntity(player("p"), "minecraft:zombie",1.0),
        )
    }

    @Test
    fun unrelatedMatchEvents_areIgnored() {
        val bus = MatchEventBus()
        val service = CompassService(bus)
        service.onMatchEvent(MatchEvent.BroadcastNotification("hi"))
    }
}
