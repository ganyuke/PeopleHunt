package io.github.ganyuke.peoplehunt.core.services.core

import io.github.ganyuke.peoplehunt.core.testutil.player
import kotlin.test.*
import kotlin.time.Clock

class MatchEngineModelsTest {
    @Test
    fun matchStatusVariants_exposeFields() {
        val runner = player("runner")
        val hunter = player("hunter")
        val now = Clock.System.now()
        val idle = MatchEngine.MatchState.Idle(runner, setOf(hunter))
        val primed = MatchEngine.MatchState.Primed(runner, setOf(hunter), now)
        val active = MatchEngine.MatchState.Active(runner, setOf(hunter), now)
        val finished = MatchEngine.MatchState.Finished(
            runner,
            setOf(hunter),
            now,
            now,
            MatchEngine.MatchOutcome.HUNTER_VICTORY,
        )
        assertEquals(runner, idle.runner)
        assertEquals(now, primed.primedAt)
        assertEquals(now, active.startedAt)
        assertEquals(MatchEngine.MatchOutcome.HUNTER_VICTORY, finished.outcome)
        assertTrue(idle != active)
    }

    @Test
    fun matchResultAndFailureReasons_areExhaustive() {
        assertIs<MatchEngine.MatchResult.Ok>(MatchEngine.MatchResult.Ok())
        assertIs<MatchEngine.MatchResult.Ok>(MatchEngine.MatchResult.Ok("ok"))
        assertEquals(
            MatchEngine.FailureReason.NOT_RUNNING,
            MatchEngine.MatchResult.Err(MatchEngine.FailureReason.NOT_RUNNING).reason,
        )
        MatchEngine.FailureReason.entries.forEach { assertEquals(it, it) }
        MatchEngine.MatchOutcome.entries.forEach { assertEquals(it, it) }
    }

    @Test
    fun matchPlayer_equalityUsesDataClassSemantics() {
        val a = player("a")
        val b = a.copy(name = "b")
        assertEquals(a, a.copy())
        assertNotEquals(a, b)
    }
}
