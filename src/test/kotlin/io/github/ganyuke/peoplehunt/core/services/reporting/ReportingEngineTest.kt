package io.github.ganyuke.peoplehunt.core.services.reporting

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.ReportableEvent
import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.events.models.KillCause
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.testutil.endPortalCompleted
import io.github.ganyuke.peoplehunt.core.testutil.playerDamagedEntity
import io.github.ganyuke.peoplehunt.core.testutil.entityDied
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.playerAcquiredItem
import io.github.ganyuke.peoplehunt.core.testutil.playerBreathChanged
import io.github.ganyuke.peoplehunt.core.testutil.playerChangedDimension
import io.github.ganyuke.peoplehunt.core.testutil.playerConnected
import io.github.ganyuke.peoplehunt.core.testutil.playerDamagedByEntity
import io.github.ganyuke.peoplehunt.core.testutil.playerDamagedByEnvironment
import io.github.ganyuke.peoplehunt.core.testutil.playerDied
import io.github.ganyuke.peoplehunt.core.testutil.playerDisconnected
import io.github.ganyuke.peoplehunt.core.testutil.playerEnteredFluid
import io.github.ganyuke.peoplehunt.core.testutil.playerEnteredStructure
import io.github.ganyuke.peoplehunt.core.testutil.playerExitedFluid
import io.github.ganyuke.peoplehunt.core.testutil.playerExitedStructure
import io.github.ganyuke.peoplehunt.core.testutil.playerFilledBucket
import io.github.ganyuke.peoplehunt.core.testutil.playerGameModeChanged
import io.github.ganyuke.peoplehunt.core.testutil.playerHealthChanged
import io.github.ganyuke.peoplehunt.core.testutil.playerHungerChanged
import io.github.ganyuke.peoplehunt.core.testutil.playerJoined
import io.github.ganyuke.peoplehunt.core.testutil.playerMoved
import io.github.ganyuke.peoplehunt.core.testutil.playerQuit
import io.github.ganyuke.peoplehunt.core.testutil.playerRespawned
import io.github.ganyuke.peoplehunt.core.testutil.playerSnapshotChanged
import io.github.ganyuke.peoplehunt.core.testutil.playerThrewEnderEye
import io.github.ganyuke.peoplehunt.core.testutil.playerXpChanged
import io.github.ganyuke.peoplehunt.core.testutil.potionEffectApplied
import io.github.ganyuke.peoplehunt.core.testutil.potionEffectRemoved
import io.github.ganyuke.peoplehunt.core.testutil.projectileHit
import io.github.ganyuke.peoplehunt.core.testutil.projectileLaunched
import io.github.ganyuke.peoplehunt.core.testutil.teleportSnapshot
import io.github.ganyuke.peoplehunt.core.testutil.pos
import io.github.ganyuke.peoplehunt.core.testutil.reportingEngineFixture
import io.github.ganyuke.peoplehunt.core.services.reporting.milestones.SpeedrunMilestone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

class ReportingEngineTest {

    // -------------------------------------------------------------------------
    // LIFECYCLE
    // -------------------------------------------------------------------------

    @Test
    fun matchStartAndEnd_resetParticipants() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        val hunter = player("hunter")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, setOf(hunter)))
        fixture.engine.onMatchEvent(
            MatchEvent.MatchEnd(
                MatchEngine.MatchState.Finished(runner, setOf(hunter), Clock.System.now(), Clock.System.now(), MatchEngine.MatchOutcome.INCONCLUSIVE),
            ),
        )
        fixture.engine.onReportableEvent(playerMoved(runner))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }

    // -------------------------------------------------------------------------
    // REPORT ERROR
    // -------------------------------------------------------------------------

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
        assertTrue(fixture.engine.participantStats.isEmpty())
        val stats = CombatStatsTracker.PlayerStats(kills = 1)
        assertEquals(1, stats.kills)
        assertEquals(stats, stats.copy())
    }

    // -------------------------------------------------------------------------
    // handlePlayerDied
    // -------------------------------------------------------------------------

    @Test
    fun playerDiedKilledByTrackedPlayer_recordsDeathAndKill() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        val hunter = player("hunter")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, setOf(hunter)))
        fixture.engine.onReportableEvent(playerDied(player = runner, cause = KillCause.KilledByPlayer(hunter)))
        assertTrue(fixture.logger.infoMessages.any { it.contains("has died") })
        assertTrue(fixture.logger.infoMessages.any { it.contains("scored a kill") })
    }

    @Test
    fun playerDiedByEnvironment_recordsDeathOnly() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDied(player = runner, cause = KillCause.Environmental))
        assertTrue(fixture.logger.infoMessages.any { it.contains("has died") })
        assertTrue(fixture.logger.infoMessages.none { it.contains("scored a kill") })
    }

    @Test
    fun playerDiedByEntity_recordsDeathOnly() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDied(player = runner, cause = KillCause.KilledByEntity("minecraft:skeleton")))
        assertTrue(fixture.logger.infoMessages.any { it.contains("has died") })
        assertTrue(fixture.logger.infoMessages.none { it.contains("scored a kill") })
    }

    @Test
    fun playerDied_untrackedPlayer_warnsBoth() {
        val fixture = reportingEngineFixture()
        val stranger = player("stranger")
        fixture.engine.onReportableEvent(playerDied(player = stranger, cause = KillCause.KilledByPlayer(stranger)))
        assertEquals(2, fixture.logger.warnMessages.size)
    }

    // -------------------------------------------------------------------------
    // handleEntityDied
    // -------------------------------------------------------------------------

    @Test
    fun entityKilledByTrackedPlayer_recordsKill() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        val hunter = player("hunter")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, setOf(hunter)))
        fixture.engine.onReportableEvent(entityDied("minecraft:zombie", cause = KillCause.KilledByPlayer(hunter)))
        assertTrue(fixture.logger.infoMessages.any { it.contains("scored a kill against minecraft:zombie") })
    }

    @Test
    fun entityKilledByEnvironment_ignored() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(entityDied("minecraft:zombie", cause = KillCause.Environmental))
        assertTrue(fixture.logger.infoMessages.none { it.contains("scored a kill") })
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
    // handleDamageDealt
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

    // -------------------------------------------------------------------------
    // handleDamageReceived
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
    // handlePotionEffectApplied
    // -------------------------------------------------------------------------

    @Test
    fun potionAppliedToRunner_logsApplied() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(potionEffectApplied(runner, effectType = "minecraft:strength", reapplication = false))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Applied minecraft:strength") })
    }

    @Test
    fun potionReappliedToRunner_logsReapplied() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(potionEffectApplied(runner, effectType = "minecraft:speed", reapplication = true))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Reapplied minecraft:speed") })
    }

    @Test
    fun potionAppliedToUntracked_ignored() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(potionEffectApplied(player("stranger")))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Potion") })
    }

    // -------------------------------------------------------------------------
    // handlePotionEffectRemoved
    // -------------------------------------------------------------------------

    @Test
    fun potionRemovedFromRunner_logsRemoved() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(potionEffectRemoved(runner, effectType = "minecraft:regeneration"))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Removed minecraft:regeneration") })
    }

    @Test
    fun potionRemovedFromUntracked_ignored() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(potionEffectRemoved(player("stranger")))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Potion") })
    }

    // -------------------------------------------------------------------------
    // handleSnapshot
    // -------------------------------------------------------------------------

    @Test
    fun snapshotFromRunner_logsSnapshot() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerSnapshotChanged(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Snapshot") && it.contains("runner") })
    }

    @Test
    fun snapshotFromUntracked_ignored() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerSnapshotChanged(player("stranger")))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Snapshot") })
    }

    // -------------------------------------------------------------------------
    // General payload logging handlers
    // -------------------------------------------------------------------------

    @Test
    fun trackedPlayerMovement_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerMoved(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Movement: runner") })
    }

    @Test
    fun untrackedPlayerMovement_ignored() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerMoved(player("stranger")))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Movement:") })
    }

    @Test
    fun trackedPlayerRespawned_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerRespawned(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Respawn: runner") })
    }

    @Test
    fun trackedTeleport_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(teleportSnapshot(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Teleport: runner") })
    }

    @Test
    fun trackedGameModeChanged_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerGameModeChanged(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Gamemode: runner") })
    }

    @Test
    fun trackedPlayerConnected_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerConnected(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Connect: runner") })
    }

    @Test
    fun trackedPlayerDisconnected_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDisconnected(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Disconnect: runner") })
    }

    @Test
    fun trackedStructureExit_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerExitedStructure(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("StructureExit: runner") })
    }

    @Test
    fun trackedFluidEnter_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerEnteredFluid(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("FluidEnter: runner") })
    }

    @Test
    fun trackedFluidExit_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerExitedFluid(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("FluidExit: runner") })
    }

    @Test
    fun trackedHealthChanged_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerHealthChanged(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Health: runner") })
    }

    @Test
    fun trackedHungerChanged_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerHungerChanged(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Hunger: runner") })
    }

    @Test
    fun trackedBreathChanged_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerBreathChanged(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Breath: runner") })
    }

    @Test
    fun trackedXpChanged_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerXpChanged(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("XP: runner") })
    }

    @Test
    fun trackedJoin_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerJoined(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Join: runner") })
    }

    @Test
    fun trackedQuit_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerQuit(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Quit: runner") })
    }

    // -------------------------------------------------------------------------
    // handlePlayerDamagedByEnvironment
    // -------------------------------------------------------------------------

    @Test
    fun trackedPlayerDamagedByEnvironment_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedByEnvironment(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("EnvironmentDamage: runner") })
    }

    @Test
    fun untrackedPlayerDamagedByEnvironment_ignored() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedByEnvironment(player("stranger")))
        assertTrue(fixture.logger.infoMessages.none { it.contains("EnvironmentDamage") })
    }

    // -------------------------------------------------------------------------
    // handleProjectileLaunched / handleProjectileHit
    // -------------------------------------------------------------------------

    @Test
    fun projectileLaunched_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(projectileLaunched(shooter = runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Projectile") && it.contains("launched") })
    }

    @Test
    fun projectileHit_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(projectileHit(shooter = runner, hitPlayer = player("hunter")))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Projectile") && it.contains("hit") })
    }

    @Test
    fun projectileLaunchedByNonPlayer_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(projectileLaunched(shooter = null))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Projectile") && it.contains("unknown") })
    }

    @Test
    fun projectileLaunchedByEntity_logsEntityType() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(projectileLaunched(shooter = null, shooterIdentifier = "minecraft:skeleton"))
        assertTrue(fixture.logger.infoMessages.any { it.contains("minecraft:skeleton") })
    }

    @Test
    fun projectileHitByEntity_logsEntityType() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(projectileHit(shooter = null, shooterIdentifier = "minecraft:pillager"))
        assertTrue(fixture.logger.infoMessages.any { it.contains("minecraft:pillager") })
    }

    @Test
    fun endCrystalDiscovered_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        val crystalPos = pos()
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(
            ReportableEvent(
                tick = 0,
                payload = ReportablePayload.EndCrystalDiscovered(crystalPos, 42),
            )
        )
        assertTrue(fixture.logger.infoMessages.any { it.contains("EndCrystal") })
    }

    @Test
    fun endPortalCompleted_logs() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(endPortalCompleted(pos()))
        assertTrue(fixture.logger.infoMessages.any { it.contains("EndPortal") })
    }

    // -------------------------------------------------------------------------
    // processMilestoneTracking — PlayerEnteredStructure
    // -------------------------------------------------------------------------

    @Test
    fun runnerEntersFortress_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerEnteredStructure(runner, "minecraft:fortress"))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun runnerEntersBastionAndStronghold_tracksMilestones() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerEnteredStructure(runner, "minecraft:fortress"))
        fixture.engine.onReportableEvent(playerEnteredStructure(runner, "minecraft:bastion_remnant"))
        fixture.engine.onReportableEvent(playerEnteredStructure(runner, "minecraft:stronghold"))
        assertEquals(3, fixture.logger.infoMessages.count { it.contains("Milestone Unlocked") })
    }

    @Test
    fun nonRunnerStructure_ignored() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerEnteredStructure(player("other"), "minecraft:fortress"))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }

    @Test
    fun unknownStructure_doesNotUnlockMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerEnteredStructure(runner, "minecraft:village"))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }

    // -------------------------------------------------------------------------
    // processMilestoneTracking — PlayerAcquiredItem
    // -------------------------------------------------------------------------

    @Test
    fun runnerAcquiresItem_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerAcquiredItem(runner, SpeedrunMilestone.ItemAcquired.Item.IRON_INGOT, SpeedrunMilestone.AcquisitionMethod.PICKED_UP))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun nonRunnerAcquiresItem_noMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerAcquiredItem(player("other"), SpeedrunMilestone.ItemAcquired.Item.IRON_INGOT, SpeedrunMilestone.AcquisitionMethod.PICKED_UP))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }

    // -------------------------------------------------------------------------
    // processMilestoneTracking — PlayerFilledBucket
    // -------------------------------------------------------------------------

    @Test
    fun runnerPicksUpWaterBucket_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerFilledBucket(runner, "minecraft:water"))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun runnerPicksUpLavaBucket_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerFilledBucket(runner, "minecraft:lava"))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun runnerPicksUpMilkBucket_noMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerFilledBucket(runner, "minecraft:milk"))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }

    // -------------------------------------------------------------------------
    // processMilestoneTracking — PlayerChangedDimension
    // -------------------------------------------------------------------------

    @Test
    fun runnerEntersNether_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerChangedDimension(runner, "minecraft:overworld", "minecraft:the_nether"))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun runnerLeavesNetherWithBlazeRod_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        // first pick up blaze rod to unlock the LeftNether gate
        fixture.engine.onReportableEvent(playerAcquiredItem(runner, SpeedrunMilestone.ItemAcquired.Item.BLAZE_ROD, SpeedrunMilestone.AcquisitionMethod.PICKED_UP))
        fixture.engine.onReportableEvent(playerChangedDimension(runner, "minecraft:the_nether", "minecraft:overworld"))
        assertTrue(fixture.logger.infoMessages.count { it.contains("Milestone Unlocked") } >= 2)
    }

    @Test
    fun runnerLeavesNetherWithoutBlazeRod_noMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerChangedDimension(runner, "minecraft:the_nether", "minecraft:overworld"))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }

    @Test
    fun runnerEntersEnd_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerChangedDimension(runner, "minecraft:overworld", "minecraft:the_end"))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun nonRunnerDimensionChange_noMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerChangedDimension(player("other"), "minecraft:overworld", "minecraft:the_nether"))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }

    // -------------------------------------------------------------------------
    // processMilestoneTracking — PlayerThrewEnderEye
    // -------------------------------------------------------------------------

    @Test
    fun runnerThrowsEyeOfEnder_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerThrewEnderEye(runner))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    // -------------------------------------------------------------------------
    // processMilestoneTracking — EndPortalCompleted
    // -------------------------------------------------------------------------

    @Test
    fun endPortalCompleted_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(endPortalCompleted(pos()))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    // -------------------------------------------------------------------------
    // processMilestoneTracking — End Crystal destroyed via PlayerDamagedEntity
    // -------------------------------------------------------------------------

    @Test
    fun firstEndCrystalDestroyed_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:end_crystal", 0.0))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun secondEndCrystalDestroyed_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:end_crystal", 0.0))
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:end_crystal", 0.0))
        assertEquals(2, fixture.logger.infoMessages.count { it.contains("Milestone Unlocked") })
    }

    // -------------------------------------------------------------------------
    // processMilestoneTracking — EntityDied (dragon)
    // -------------------------------------------------------------------------

    @Test
    fun dragonSlainByRunner_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(entityDied("minecraft:ender_dragon", cause = KillCause.KilledByPlayer(runner)))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun dragonSlainByHunter_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        val hunter = player("hunter")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, setOf(hunter)))
        fixture.engine.onReportableEvent(entityDied("minecraft:ender_dragon", cause = KillCause.KilledByPlayer(hunter)))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun dragonSlainByEntity_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(entityDied("minecraft:ender_dragon", cause = KillCause.KilledByEntity("minecraft:tnt")))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun dragonSlainEnvironmental_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(entityDied("minecraft:ender_dragon", cause = KillCause.Environmental))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun nonDragonDeath_noMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(entityDied("minecraft:zombie"))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }

    // -------------------------------------------------------------------------
    // processMilestoneTracking — PlayerDamagedEntity (dragon health %)
    // -------------------------------------------------------------------------

    @Test
    fun dragonHealthBelow50Percent_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:ender_dragon", 10.0, remainingHealth = 90.0))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun dragonHealthBelow25Percent_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:ender_dragon", 10.0, remainingHealth = 45.0))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun dragonHealthBelow10Percent_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:ender_dragon", 10.0, remainingHealth = 15.0))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun dragonHealthBelow5Percent_logsMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:ender_dragon", 10.0, remainingHealth = 5.0))
        assertTrue(fixture.logger.infoMessages.any { it.contains("Milestone Unlocked") })
    }

    @Test
    fun dragonHealthAbove50Percent_noMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:ender_dragon", 10.0, remainingHealth = 150.0))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }

    @Test
    fun nonRunnerDamagesDragon_noMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedEntity(player("other"), "minecraft:ender_dragon", 10.0, remainingHealth = 5.0))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }

    @Test
    fun runnerDamagesNonDragon_noMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:zombie", 10.0, remainingHealth = 5.0))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }

    @Test
    fun dragonDamageWithNullHealth_noMilestone() {
        val fixture = reportingEngineFixture()
        val runner = player("runner")
        fixture.engine.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
        fixture.engine.onReportableEvent(playerDamagedEntity(runner, "minecraft:ender_dragon", 10.0))
        assertTrue(fixture.logger.infoMessages.none { it.contains("Milestone Unlocked") })
    }
}
