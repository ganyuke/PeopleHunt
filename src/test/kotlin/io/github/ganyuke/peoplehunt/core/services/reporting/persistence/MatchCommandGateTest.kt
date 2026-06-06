package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.reportStenographerFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * Exercises the same session gate logic used by [io.github.ganyuke.peoplehunt.paper.command.match.MatchCommand.guardSession].
 */
class MatchCommandGateTest {
  @Test
  fun closedSession_allowsPrimeAndStart() {
    val fixture = reportStenographerFixture()
    assertNull(fixture.stenographer.blockReason())
  }

  @Test
  fun recordingSession_blocksPrimeAndStart() = runBlocking {
    val fixture = reportStenographerFixture()
    fixture.stenographer.onMatchEvent(MatchEvent.MatchStart(player("runner"), emptySet()))
    delay(50)
    assertEquals(ReportSessionBlockReason.SESSION_ALREADY_ACTIVE, fixture.stenographer.blockReason())
  }

  @Test
  fun openFailedSession_blocksPrimeAndStart() = runBlocking {
    val fixture = reportStenographerFixture()
    fixture.storage.failOpen = true
    fixture.stenographer.onMatchEvent(MatchEvent.MatchStart(player("runner"), emptySet()))
    delay(50)
    assertEquals(ReportSessionBlockReason.DATABASE_OPEN_FAILED, fixture.stenographer.blockReason())
  }

  @Test
  fun finalizePendingSession_blocksPrimeAndStart() = runBlocking {
    val fixture = reportStenographerFixture()
    val runner = player("runner")
    fixture.stenographer.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
    delay(50)
    fixture.storage.failFinalize = true
    fixture.stenographer.onMatchEvent(
      MatchEvent.MatchEnd(
        MatchEngine.MatchState.Finished(
          runner, emptySet(), Clock.System.now(), Clock.System.now(), MatchEngine.MatchOutcome.INCONCLUSIVE,
        ),
      ),
    )
    delay(100)
    assertEquals(ReportSessionBlockReason.FINALIZE_PENDING, fixture.stenographer.blockReason())
  }
}
