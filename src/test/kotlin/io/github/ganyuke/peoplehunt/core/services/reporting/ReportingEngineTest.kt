package io.github.ganyuke.peoplehunt.core.services.reporting

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.KillCause
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.testutil.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class ReportingEngineTest {

    // -------------------------------------------------------------------------
    // MATCH LIFECYCLE
    // -------------------------------------------------------------------------

    @Test
    fun matchStart_setsParticipants() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        val hunter = player("hunter")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, setOf(hunter)))
        // After match start, events from runner/hunter are tracked
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:zombie", 5.0))
        val stats = fixture.engine.participantStats.single { it.first.uuid == runner.uuid }.second
        assertEquals(5.0, stats.damageDealt)
    }

    @Test
    fun matchEnd_clearsParticipants() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        val hunter = player("hunter")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, setOf(hunter)))
        fixture.engine.onMatchEvent(
            MatchEvent.MatchEnd(
                MatchEngine.MatchState.Finished(runner, setOf(hunter), Clock.System.now(), Clock.System.now(), MatchEngine.MatchOutcome.INCONCLUSIVE),
            ),
        )
        // After match end, events are not tracked
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:zombie", 5.0))
        assertTrue(fixture.engine.participantStats.isEmpty())
    }

    // -------------------------------------------------------------------------
    // PARTICIPANT STATS
    // -------------------------------------------------------------------------

    @Test
    fun getParticipantStats_returnsEmptyInitially() {
        val fixture = reportingEngineFixture()
        assertTrue(fixture.engine.participantStats.isEmpty())
    }

    // -------------------------------------------------------------------------
    // COMBAT: PLAYER DIED
    // -------------------------------------------------------------------------

    @Test
    fun playerDiedKilledByTrackedPlayer_recordsDeathAndKill() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        val hunter = player("hunter")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, setOf(hunter)))
        fixture.engine.onReportableEvent(playerDied(player = runner, cause = KillCause.KilledByPlayer(hunter)))
        val runnerStats = fixture.engine.participantStats.single { it.first.uuid == runner.uuid }.second
        val hunterStats = fixture.engine.participantStats.single { it.first.uuid == hunter.uuid }.second
        assertEquals(1L, runnerStats.deaths)
        assertEquals(1L, hunterStats.kills)
    }

    @Test
    fun playerDiedByEnvironment_recordsDeathOnly() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDied(player = runner, cause = KillCause.Environmental))
        val stats = fixture.engine.participantStats.single { it.first.uuid == runner.uuid }.second
        assertEquals(1L, stats.deaths)
        assertEquals(0L, stats.kills)
    }

    @Test
    fun playerDiedByEntity_recordsDeathOnly() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDied(player = runner, cause = KillCause.KilledByEntity("minecraft:skeleton")))
        val stats = fixture.engine.participantStats.single { it.first.uuid == runner.uuid }.second
        assertEquals(1L, stats.deaths)
    }

    @Test
    fun playerDied_untrackedPlayer_noStatsRecorded() {
        val fixture = reportingEngineFixture()
        val stranger = player("stranger")
        fixture.engine.onReportableEvent(playerDied(player = stranger, cause = KillCause.KilledByPlayer(stranger)))
        assertTrue(fixture.engine.participantStats.isEmpty())
    }

    @Test
    fun playerDied_untrackedPlayer_warnsBoth() {
        val fixture = reportingEngineFixture()
        val stranger = player("stranger")
        fixture.engine.onReportableEvent(playerDied(player = stranger, cause = KillCause.KilledByPlayer(stranger)))
        assertEquals(2, fixture.logger.warnMessages.size)
    }

    // -------------------------------------------------------------------------
    // COMBAT: ENTITY DIED
    // -------------------------------------------------------------------------

    @Test
    fun entityKilledByTrackedPlayer_recordsKill() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        val hunter = player("hunter")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, setOf(hunter)))
        fixture.engine.onReportableEvent(entityDied("minecraft:zombie", cause = KillCause.KilledByPlayer(hunter)))
        val hunterStats = fixture.engine.participantStats.single { it.first.uuid == hunter.uuid }.second
        assertEquals(1L, hunterStats.kills)
    }

    @Test
    fun entityKilledByEnvironment_noStats() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(entityDied("minecraft:zombie", cause = KillCause.Environmental))
        assertTrue(fixture.engine.participantStats.isEmpty())
    }

    @Test
    fun entityKilledByUntrackedPlayer_warns() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(entityDied("minecraft:zombie", cause = KillCause.KilledByPlayer(player("stranger"))))
        assertEquals(1, fixture.logger.warnMessages.size)
    }

    // -------------------------------------------------------------------------
    // COMBAT: DAMAGE DEALT
    // -------------------------------------------------------------------------

    @Test
    fun runnerDamageDealt_recordsDamage() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:zombie", 5.0))
        val stats = fixture.engine.participantStats.single { it.first.uuid == runner.uuid }.second
        assertEquals(5.0, stats.damageDealt)
    }

    @Test
    fun multipleDamageDealt_accumulates() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:zombie", 3.0))
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:zombie", 2.5))
        val stats = fixture.engine.participantStats.single { it.first.uuid == runner.uuid }.second
        assertEquals(5.5, stats.damageDealt)
    }

    // -------------------------------------------------------------------------
    // COMBAT: DAMAGE TAKEN
    // -------------------------------------------------------------------------

    @Test
    fun runnerDamageReceived_recordsDamage() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedByEntity(runner, "minecraft:zombie", 3.0))
        val stats = fixture.engine.participantStats.single { it.first.uuid == runner.uuid }.second
        assertEquals(3.0, stats.damageTaken)
    }

    @Test
    fun untrackedDamageReceived_ignored() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedByEntity(player("stranger"), "minecraft:zombie", 3.0))
        assertTrue(fixture.engine.participantStats.isEmpty())
    }

    // -------------------------------------------------------------------------
    // STRUCTURE VISIT TRACKING
    // -------------------------------------------------------------------------

    @Test
    fun runnerEntersFortress_logsFirstVisit() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerEnteredStructure(runner, "minecraft:fortress"))
        assertTrue(fixture.logger.infoMessages.any { it.contains("GlobalStructureFirstVisit") && it.contains("minecraft:fortress") })
    }

    @Test
    fun runnerEntersFortressTwice_onlyFirstVisitLogged() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        val structurePos = pos()
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerEnteredStructure(runner, "minecraft:fortress", structurePos))
        fixture.engine.onReportableEvent(playerEnteredStructure(runner, "minecraft:fortress", structurePos))
        assertEquals(1, fixture.logger.infoMessages.count { it.contains("GlobalStructureFirstVisit") })
    }

    @Test
    fun nonRunnerStructure_stillLogsFirstVisit() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerEnteredStructure(player("other"), "minecraft:fortress"))
        // Structure visit tracking is independent of player tracking
        assertTrue(fixture.logger.infoMessages.any { it.contains("GlobalStructureFirstVisit") })
    }

    @Test
    fun runnerEntersMultipleStructures_allFirstVisitsLogged() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerEnteredStructure(runner, "minecraft:fortress"))
        fixture.engine.onReportableEvent(playerEnteredStructure(runner, "minecraft:bastion_remnant"))
        fixture.engine.onReportableEvent(playerEnteredStructure(runner, "minecraft:stronghold"))
        assertEquals(3, fixture.logger.infoMessages.count { it.contains("GlobalStructureFirstVisit") })
    }

    // -------------------------------------------------------------------------
    // DEBUG LOGGING: TRACKED PLAYER EVENTS
    // -------------------------------------------------------------------------

    @Test
    fun trackedPlayerMovement_producesDebugLog() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerMoved(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("PlayerMoved") })
    }

    @Test
    fun untrackedPlayerMovement_noDebugLog() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerMoved(player("stranger")))
        assertTrue(fixture.logger.infoMessages.none { it.contains("PlayerMoved") })
    }

    @Test
    fun trackedPlayerSnapshot_producesFormattedLog() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerSnapshotChanged(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Snapshot") && it.contains("runner") })
    }

    @Test
    fun untrackedPlayerSnapshot_noLog() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerSnapshotChanged(player("stranger")))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Snapshot") })
    }

    @Test
    fun projectileMoved_alwaysSuppressed() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(
            ReportableEvent(tick = 0, payload = ReportablePayload.ProjectileMoved(1, pos(), io.github.ganyuke.peoplehunt.core.events.models.Velocity(0.0, 0.0, 0.0)))
        )
        assertTrue(fixture.logger.infoMessages.none { it.contains("ProjectileMoved") })
    }

    @Test
    fun nearbyMobs_alwaysSuppressed() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(nearbyMobs(runner))
        assertTrue(fixture.logger.infoMessages.none { it.contains("NearbyMobs") })
    }

    @Test
    fun trackedPlayerVariousEvents_producesDebugLogs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        // Send a variety of events — each should produce a generic debug log
        fixture.engine.onReportableEvent(playerEnteredFluid(runner))
        fixture.engine.onReportableEvent(playerSetSpawn(runner))
        fixture.engine.onReportableEvent(endPortalCompleted(pos()))
        // At least 3 debug logs for the 3 tracked events above
        assertTrue(fixture.logger.infoMessages.size >= 3)
    }

    // -------------------------------------------------------------------------
    // WARN LOGGING: UNTRACKED PLAYERS
    // -------------------------------------------------------------------------

    @Test
    fun untrackedPlayerDeath_producesWarnings() {
        val fixture = reportingEngineFixture()
        val stranger = player("stranger")
        fixture.engine.onReportableEvent(playerDied(stranger, cause = KillCause.KilledByPlayer(stranger)))
        assertTrue(fixture.logger.warnMessages.any { it.contains("untracked player") })
        assertTrue(fixture.logger.warnMessages.any { it.contains("untracked killer") })
    }

    @Test
    fun untrackedKillerEntity_producesWarning() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(entityDied("minecraft:zombie", cause = KillCause.KilledByPlayer(player("stranger"))))
        assertTrue(fixture.logger.warnMessages.any { it.contains("untracked killer") })
    }
}
