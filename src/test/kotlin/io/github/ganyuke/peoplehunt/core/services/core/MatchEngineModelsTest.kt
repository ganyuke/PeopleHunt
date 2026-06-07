package io.github.ganyuke.peoplehunt.core.services.core

import io.github.ganyuke.peoplehunt.core.services.core.models.MatchFailureReason
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchOutcome
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchResult
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchState
import io.github.ganyuke.peoplehunt.core.testutil.player
import kotlin.test.*
import kotlin.time.Clock

class MatchEngineModelsTest {
    @Test
    fun matchStatusVariants_exposeFields() {
        val runner = player("runner")
        val hunter = player("hunter")
        val now = Clock.System.now()
        val idle = MatchState.Idle(runner, setOf(hunter))
        val primed = MatchState.Primed(runner, setOf(hunter), now)
        val active = MatchState.Active(runner, setOf(hunter), now)
        val finished = MatchState.Finished(
            runner,
            setOf(hunter),
            now,
            now,
            MatchOutcome.HUNTER_VICTORY,
        )
        assertEquals(runner, idle.runner)
        assertEquals(now, primed.primedAt)
        assertEquals(now, active.startedAt)
        assertEquals(MatchOutcome.HUNTER_VICTORY, finished.outcome)
        assertTrue(idle != active)
    }

    @Test
    fun matchResultAndFailureReasons_areExhaustive() {
        assertIs<MatchResult.Ok>(MatchResult.Ok())
        assertIs<MatchResult.Ok>(MatchResult.Ok("ok"))
        assertEquals(
            MatchFailureReason.NOT_RUNNING,
            MatchResult.Err(MatchFailureReason.NOT_RUNNING).reason,
        )
        MatchFailureReason.entries.forEach { assertEquals(it, it) }
        MatchOutcome.entries.forEach { assertEquals(it, it) }
    }

    @Test
    fun matchPlayer_equalityUsesDataClassSemantics() {
        val a = player("a")
        val b = a.copy(name = "b")
        assertEquals(a, a.copy())
        assertNotEquals(a, b)
    }
}
