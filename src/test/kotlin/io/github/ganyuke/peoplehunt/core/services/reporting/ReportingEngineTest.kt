package io.github.ganyuke.peoplehunt.core.services.reporting

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.testutil.FakeStructureLocator
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.pos
import io.github.ganyuke.peoplehunt.core.testutil.reportingEngineFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class ReportingEngineTest {
    @Test
    fun matchStartAndEnd_resetParticipants() {
        val location = pos()
        val fixture = reportingEngineFixture(
            structures = FakeStructureLocator(mapOf(location to "minecraft:fortress")),
        )
        val runner = player("runner")
        val hunter = player("hunter")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, setOf(hunter)))
        fixture.engine.onMatchEvent(
            MatchEvent.MatchEnd(
                MatchEngine.MatchStatus.Finished(
                    runner,
                    listOf(hunter),
                    Clock.System.now(),
                    Clock.System.now(),
                    MatchEngine.MatchOutcome.INCONCLUSIVE,
                ),
            ),
        )
        fixture.engine.onReportableEvent(ReportableEvent.PlayerMoved(runner, location))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }

    @Test
    fun runnerEntersFortress_logsMilestone() {
        val location = pos()
        val fixture = reportingEngineFixture(
            structures = FakeStructureLocator(mapOf(location to "minecraft:fortress")),
        )
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(ReportableEvent.PlayerMoved(runner, location))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun runnerEntersBastionAndStronghold_tracksMilestones() {
        val fortressPos = pos(1, 0, 0)
        val bastionPos = pos(2, 0, 0)
        val strongholdPos = pos(3, 0, 0)
        val fixture = reportingEngineFixture(
            structures = FakeStructureLocator(
                mapOf(
                    fortressPos to "minecraft:fortress",
                    bastionPos to "minecraft:bastion_remnant",
                    strongholdPos to "minecraft:stronghold",
                ),
            ),
        )
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(ReportableEvent.PlayerMoved(runner, fortressPos))
        fixture.engine.onReportableEvent(ReportableEvent.PlayerMoved(runner, bastionPos))
        fixture.engine.onReportableEvent(ReportableEvent.PlayerMoved(runner, strongholdPos))
        assertEquals(3, fixture.logger.infoMessages.count { it.contains("Milestone Unlocked") })
    }

    @Test
    fun nonRunnerMovement_ignored() {
        val location = pos()
        val fixture = reportingEngineFixture(
            structures = FakeStructureLocator(mapOf(location to "minecraft:fortress")),
        )
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(ReportableEvent.PlayerMoved(player("other"), location))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }

    @Test
    fun entityDied_tracksRunnerAndHunterStats() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        val hunter = player("hunter")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, setOf(hunter)))
        fixture.engine.onReportableEvent(
            ReportableEvent.EntityDied(
                player = runner,
                entityIdentifier = "minecraft:player",
                pos = pos(),
                playerKiller = hunter,
                entityKiller = null,
            ),
        )
        assertTrue(fixture.logger.infoMessages.any { it.contains("has died") })
        assertTrue(fixture.logger.infoMessages.any { it.contains("scored a kill") })
    }

    @Test
    fun entityDied_warnsForUntrackedPlayers() {
        val fixture = reportingEngineFixture()
        val stranger = player("stranger")
        fixture.engine.onReportableEvent(
            ReportableEvent.EntityDied(
                player = stranger,
                entityIdentifier = "minecraft:zombie",
                pos = pos(),
                playerKiller = stranger,
                entityKiller = null,
            ),
        )
        assertEquals(2, fixture.logger.warnMessages.size)
    }

    @Test
    fun reportError_notifiesOperators() {
        val fixture = reportingEngineFixture()
        val notifications = mutableListOf<MatchEvent>()
        fixture.bus.register { notifications += it }
        fixture.engine.reportError("disk full")
        assertTrue(fixture.scheduler.mainThreadTasks.isNotEmpty())
        assertTrue(notifications.any { it is MatchEvent.OperatorNotification })
    }

    @Test
    fun getParticipantStats_returnsEmptyList() {
        val fixture = reportingEngineFixture()
        assertTrue(fixture.engine.getParticipantStats().isEmpty())
        val stats = ReportingEngine.ParticipantStats(player("p"), kills = 1, deaths = 2)
        assertEquals(1, stats.kills)
        assertEquals(stats, stats.copy())
    }

    @Test
    fun otherReportableEvents_areIgnored() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(
            ReportableEvent.PlayerDamagedEntity(runner, 3.0),
        )
        fixture.engine.onMatchEvent(MatchEvent.CompassTick)
    }

    @Test
    fun unknownStructure_doesNotUnlockMilestone() {
        val location = pos()
        val fixture = reportingEngineFixture(
            structures = FakeStructureLocator(mapOf(location to "minecraft:village")),
        )
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(ReportableEvent.PlayerMoved(runner, location))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }
}
