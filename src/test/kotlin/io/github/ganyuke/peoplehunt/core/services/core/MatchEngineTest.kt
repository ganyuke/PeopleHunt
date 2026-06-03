package io.github.ganyuke.peoplehunt.core.services.core

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.events.models.KillCause
import io.github.ganyuke.peoplehunt.core.testutil.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class MatchEngineTest {
    private fun collectEvents(bus: MatchEventBus): MutableList<MatchEvent> {
        val events = mutableListOf<MatchEvent>()
        bus.register { events += it }
        return events
    }

    @Test
    fun idleStatus_reflectsRunnerAndHunters() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        val hunter = player("hunter")
        match.setRunner(runner)
        match.addHunter(hunter)
        val status = match.currentStatus
        assertIs<MatchEngine.MatchState.Idle>(status)
        assertEquals(runner, status.runner)
        assertEquals(setOf(hunter), status.hunters)
    }

    @Test
    fun prime_requiresRunner() {
        val match = matchEngineFixture().engine
        val result = match.prime(emptyList())
        assertIs<MatchEngine.MatchResult.Err>(result)
        assertEquals(MatchEngine.FailureReason.NO_RUNNER_SPECIFIED, result.reason)
    }

    @Test
    fun prime_fillsHuntersFromOnlinePlayers() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        val hunter = player("hunter")
        match.setRunner(runner)
        match.prime(listOf(runner, hunter))
        assertEquals(setOf(hunter), match.currentStatus.hunters)
        assertIs<MatchEngine.MatchState.Primed>(match.currentStatus)
    }

    @Test
    fun prime_rejectsWhenAlreadyPrimed() {
        val match = matchEngineFixture().engine
        match.setRunner(player("runner"))
        match.prime(emptyList())
        val result = match.prime(emptyList())
        assertEquals(MatchEngine.FailureReason.ALREADY_PRIMED, (result as MatchEngine.MatchResult.Err).reason)
    }

    @Test
    fun primedRunnerMove_startsMatch() {
        val fixture = matchEngineFixture()
        val events = collectEvents(fixture.bus)
        val runner = player("runner")
        val hunter = player("hunter")
        fixture.engine.setRunner(runner)
        fixture.engine.addHunter(hunter)
        fixture.engine.prime(emptyList())

        fixture.engine.onEvent(playerMoved(runner))

        assertIs<MatchEngine.MatchState.Active>(fixture.engine.currentStatus)
        assertTrue(events.any { it is MatchEvent.MatchStart })
        assertTrue(events.any { it is MatchEvent.GiveHuntersCompass })
        assertTrue(fixture.scheduler.everyTickTasks.isNotEmpty())
    }

    @Test
    fun forceStart_startsWithoutPrime() {
        val fixture = matchEngineFixture()
        val events = collectEvents(fixture.bus)
        val runner = player("runner")
        fixture.engine.setRunner(runner)
        val result = fixture.engine.forceStart(listOf(runner, player("h1")))
        assertIs<MatchEngine.MatchResult.Ok>(result)
        assertTrue(events.any { it is MatchEvent.MatchStart })
    }

    @Test
    fun forceStart_rejectsWithoutRunner() {
        val match = matchEngineFixture().engine
        val result = match.forceStart(emptyList())
        assertEquals(MatchEngine.FailureReason.NO_RUNNER_SPECIFIED, (result as MatchEngine.MatchResult.Err).reason)
    }

    @Test
    fun forceStart_rejectsWhenActive() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        match.setRunner(runner)
        match.forceStart(emptyList())
        val result = match.forceStart(emptyList())
        assertEquals(MatchEngine.FailureReason.ALREADY_STARTED, (result as MatchEngine.MatchResult.Err).reason)
    }

    @Test
    fun forceEnd_releasesPrime() {
        val match = matchEngineFixture().engine
        match.setRunner(player("runner"))
        match.prime(emptyList())
        val result = match.forceEnd()
        assertIs<MatchEngine.MatchResult.Ok>(result)
        assertIs<MatchEngine.MatchState.Idle>(match.currentStatus)
    }

    @Test
    fun forceEnd_stopsActiveMatchAndPostsMatchEnd() {
        val fixture = matchEngineFixture()
        val events = collectEvents(fixture.bus)
        val runner = player("runner")
        fixture.engine.setRunner(runner)
        fixture.engine.forceStart(emptyList())
        val result = fixture.engine.forceEnd()
        assertIs<MatchEngine.MatchResult.Ok>(result)
        assertIs<MatchEngine.MatchState.Finished>(fixture.engine.currentStatus)
        fixture.scheduler.runAllAfterTasks()
        assertTrue(events.any { it is MatchEvent.MatchEnd })
    }

    @Test
    fun forceEnd_notRunningWhenIdle() {
        val match = matchEngineFixture().engine
        val result = match.forceEnd()
        assertEquals(MatchEngine.FailureReason.NOT_RUNNING, (result as MatchEngine.MatchResult.Err).reason)
    }

    @Test
    fun runnerDeath_endsWithHunterVictory() {
        val fixture = matchEngineFixture()
        collectEvents(fixture.bus)
        val runner = player("runner")
        val hunter = player("hunter")
        fixture.engine.setRunner(runner)
        fixture.engine.forceStart(emptyList())
        fixture.engine.onEvent(
            playerDied(
                player = runner,
                cause = KillCause.KilledByPlayer(hunter),
            ),
        )
        val finished = fixture.engine.currentStatus as MatchEngine.MatchState.Finished
        assertEquals(MatchEngine.MatchOutcome.HUNTER_VICTORY, finished.outcome)
        fixture.scheduler.runAllAfterTasks()
    }

    @Test
    fun enderDragonDeath_endsWithRunnerVictory() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        match.setRunner(runner)
        match.forceStart(emptyList())
        match.onEvent(
            entityDied("minecraft:ender_dragon"),
        )
        val finished = match.currentStatus as MatchEngine.MatchState.Finished
        assertEquals(MatchEngine.MatchOutcome.RUNNER_VICTORY, finished.outcome)
    }

    @Test
    fun setRunner_rejectsDuplicateRoles() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        val hunter = player("hunter")
        match.setRunner(runner)
        match.addHunter(hunter)
        assertEquals(MatchEngine.FailureReason.PLAYER_ALREADY_RUNNER, (match.setRunner(runner) as MatchEngine.MatchResult.Err).reason)
        assertEquals(MatchEngine.FailureReason.PLAYER_ALREADY_HUNTER, (match.setRunner(hunter) as MatchEngine.MatchResult.Err).reason)
    }

    @Test
    fun removeRunner_requiresCurrentRunner() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        match.setRunner(runner)
        assertIs<MatchEngine.MatchResult.Ok>(match.removeRunner(runner))
        assertEquals(null, match.currentStatus.runner)
        assertEquals(MatchEngine.FailureReason.PLAYER_NOT_IN_GROUP, (match.removeRunner(runner) as MatchEngine.MatchResult.Err).reason)
    }

    @Test
    fun clearRunner_clearsRunner() {
        val match = matchEngineFixture().engine
        match.setRunner(player("runner"))
        assertIs<MatchEngine.MatchResult.Ok>(match.clearRunner())
        assertEquals(null, match.currentStatus.runner)
    }

    @Test
    fun hunterMutations_addRemoveClear() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        val hunter = player("hunter")
        match.setRunner(runner)
        assertIs<MatchEngine.MatchResult.Ok>(match.addHunter(hunter))
        assertEquals(MatchEngine.FailureReason.PLAYER_ALREADY_HUNTER, (match.addHunter(runner) as MatchEngine.MatchResult.Err).reason)
        assertIs<MatchEngine.MatchResult.Ok>(match.removeHunter(hunter))
        assertEquals(MatchEngine.FailureReason.PLAYER_NOT_IN_GROUP, (match.removeHunter(hunter) as MatchEngine.MatchResult.Err).reason)
        match.addHunter(hunter)
        assertIs<MatchEngine.MatchResult.Ok>(match.clearHunters())
        assertTrue(match.currentStatus.hunters.isEmpty())
    }

    @Test
    fun guardMutation_blocksWhilePrimedOrActive() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        match.setRunner(runner)
        match.prime(emptyList())
        assertEquals(MatchEngine.FailureReason.ALREADY_PRIMED, (match.setRunner(player("x")) as MatchEngine.MatchResult.Err).reason)
        match.onEvent(playerMoved(runner))
        assertEquals(MatchEngine.FailureReason.ALREADY_STARTED, (match.addHunter(player("h")) as MatchEngine.MatchResult.Err).reason)
    }

    @Test
    fun mutationAfterFinish_resetsToIdle() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        match.setRunner(runner)
        match.forceStart(emptyList())
        match.forceEnd()
        val result = match.addHunter(player("new"))
        assertIs<MatchEngine.MatchResult.Ok>(result)
        assertIs<MatchEngine.MatchState.Idle>(match.currentStatus)
    }

    @Test
    fun fullReset_andShutdown_clearState() {
        val fixture = matchEngineFixture()
        val runner = player("runner")
        fixture.engine.setRunner(runner)
        fixture.engine.forceStart(emptyList())
        fixture.engine.fullReset()
        assertEquals(null, fixture.engine.currentStatus.runner)
        assertTrue(fixture.engine.currentStatus.hunters.isEmpty())
        assertIs<MatchEngine.MatchState.Idle>(fixture.engine.currentStatus)
        assertTrue(fixture.scheduler.everyTickTasks.all { it.handle.cancelled })

        fixture.engine.setRunner(runner)
        fixture.engine.forceStart(emptyList())
        fixture.engine.shutdown()
        assertEquals(null, fixture.engine.currentStatus.runner)
    }

    @Test
    fun prime_afterFinished_resetsAndPrimes() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        match.setRunner(runner)
        match.forceStart(emptyList())
        match.forceEnd()
        val result = match.prime(emptyList())
        assertIs<MatchEngine.MatchResult.Ok>(result)
        assertIs<MatchEngine.MatchState.Primed>(match.currentStatus)
    }

    @Test
    fun prime_whenActive_returnsAlreadyStarted() {
        val match = matchEngineFixture().engine
        match.setRunner(player("runner"))
        match.forceStart(emptyList())
        assertEquals(MatchEngine.FailureReason.ALREADY_STARTED, (match.prime(emptyList()) as MatchEngine.MatchResult.Err).reason)
    }

    @Test
    fun nonRunnerMoveWhilePrimed_doesNotStart() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        match.setRunner(runner)
        match.prime(emptyList())
        match.onEvent(playerMoved(player("other")))
        assertIs<MatchEngine.MatchState.Primed>(match.currentStatus)
    }

    @Test
    fun entityDiedWhileIdle_isIgnored() {
        val match = matchEngineFixture().engine
        match.onEvent(entityDied("minecraft:zombie"))
        assertIs<MatchEngine.MatchState.Idle>(match.currentStatus)
    }

    @Test
    fun matchId_isStable() {
        val match = matchEngineFixture().engine
        val firstId = match.currentStatus.runner?.uuid
        val secondId = match.currentStatus.runner?.uuid
        assertEquals(firstId, secondId)
    }

    @Test
    fun currentStatus_exposesPrimedActiveAndFinishedSnapshots() {
        val fixture = matchEngineFixture(
            config = testPhConfig(matchMinutesInterval = 1.milliseconds),
        )
        val runner = player("runner")
        fixture.engine.setRunner(runner)
        fixture.engine.addHunter(player("hunter"))
        fixture.engine.prime(emptyList())
        assertIs<MatchEngine.MatchState.Primed>(fixture.engine.currentStatus)

        fixture.engine.forceStart(emptyList())
        assertIs<MatchEngine.MatchState.Active>(fixture.engine.currentStatus)
        fixture.scheduler.runAllEveryTickTasks()
        runBlocking { delay(30.milliseconds) }

        fixture.engine.forceEnd()
        val finished = fixture.engine.currentStatus as MatchEngine.MatchState.Finished
        assertEquals(fixture.engine.lastMatchResult, finished)
        fixture.scheduler.runAllAfterTasks()
    }

    @Test
    fun forceStart_skipsHunterFillWhenAlreadyPresent() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        val hunter = player("hunter")
        match.setRunner(runner)
        match.addHunter(hunter)
        match.forceStart(listOf(player("online")))
        assertEquals(setOf(hunter), match.currentStatus.hunters)
    }

    @Test
    fun primedNonMoveEvents_areIgnored() {
        val match = matchEngineFixture().engine
        match.setRunner(player("runner"))
        match.prime(emptyList())
        match.onEvent(entityDied("minecraft:sheep"))
        assertIs<MatchEngine.MatchState.Primed>(match.currentStatus)
    }

    @Test
    fun forceStart_fromFinished_restartsMatch() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        match.setRunner(runner)
        match.forceStart(emptyList())
        match.forceEnd()
        assertIs<MatchEngine.MatchResult.Ok>(match.forceStart(emptyList()))
        assertIs<MatchEngine.MatchState.Active>(match.currentStatus)
    }

    @Test
    fun prime_keepsExistingHunters() {
        val match = matchEngineFixture().engine
        val runner = player("runner")
        val hunter = player("hunter")
        match.setRunner(runner)
        match.addHunter(hunter)
        match.prime(listOf(player("ignored")))
        assertEquals(setOf(hunter), match.currentStatus.hunters)
    }

    @Test
    fun activeEntityDeath_ignoresUnrelatedEntities() {
        val match = matchEngineFixture().engine
        match.setRunner(player("runner"))
        match.forceStart(emptyList())
        match.onEvent(entityDied("minecraft:zombie"))
        assertIs<MatchEngine.MatchState.Active>(match.currentStatus)
    }

    @Test
    fun reset_clearsPrimedStateWithoutCancellingIntervalScope() {
        val match = matchEngineFixture().engine
        match.setRunner(player("runner"))
        match.prime(emptyList())
        match.reset()
        assertIs<MatchEngine.MatchState.Idle>(match.currentStatus)
    }
}
