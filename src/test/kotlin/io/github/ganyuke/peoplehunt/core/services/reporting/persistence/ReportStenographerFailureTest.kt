package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.playerDied
import io.github.ganyuke.peoplehunt.core.testutil.reportStenographerFixture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ReportStenographerFailureTest {
  @Test
  fun appendFailure_retainsBuffersUntilSuccess() = runBlocking {
    val fixture = reportStenographerFixture()
    val runner = player("runner")
    fixture.stenographer.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
    delay(200)

    fixture.stenographer.onReportableEvent(playerDied(runner))
    fixture.storage.failAppend = true
    val failed = fixture.stenographer.manualFlush()
    assertIs<ReportOpResult.Err>(failed)
    assertEquals(ReportOpFailure.WRITE_FAILED, failed.reason)

    fixture.storage.failAppend = false
    val ok = fixture.stenographer.manualFlush()
    assertIs<ReportOpResult.Ok>(ok)
    assertEquals(1, fixture.storage.appendCalls.size)
    assertEquals(1, fixture.storage.appendCalls.single().batch.events.size)
  }

  @Test
  fun openFailure_blocksNewSessionUntilCleared() = runBlocking {
    val fixture = reportStenographerFixture()
    fixture.storage.failOpen = true
    val runner = player("runner")
    fixture.stenographer.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
    delay(200)

    assertEquals(ReportSessionBlockReason.DATABASE_OPEN_FAILED, fixture.stenographer.blockReason())
  }

  @Test
  fun openFailureRecovery_manualFlushOpensDatabase() = runBlocking {
    val fixture = reportStenographerFixture()
    fixture.storage.failOpen = true
    val runner = player("runner")
    fixture.stenographer.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
    delay(200)

    fixture.storage.failOpen = false
    val result = fixture.stenographer.manualFlush()
    assertIs<ReportOpResult.Ok>(result)
    assertEquals(1, fixture.storage.openMatchCalls.size)
  }

  @Test
  fun clear_resetsSessionToClosed() = runBlocking {
    val fixture = reportStenographerFixture()
    val runner = player("runner")
    fixture.stenographer.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
    delay(200)

    val cleared = fixture.stenographer.clear()
    assertIs<ReportOpResult.Ok>(cleared)
    assertNull(fixture.stenographer.blockReason())
    assertEquals(1, fixture.storage.closeActiveCalls)
  }

  @Test
  fun matchEnd_fromOpenFailed_recoversAndFinalizes() = runBlocking {
    val fixture = reportStenographerFixture()
    val runner = player("runner")
    fixture.storage.failOpen = true
    fixture.stenographer.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
    delay(200)
    fixture.stenographer.onReportableEvent(playerDied(runner))

    fixture.storage.failOpen = false
    fixture.stenographer.onMatchEvent(
      MatchEvent.MatchEnd(
        MatchEngine.MatchState.Finished(
          runner, emptySet(), Clock.System.now(), Clock.System.now(), MatchEngine.MatchOutcome.HUNTER_VICTORY,
        ),
      ),
    )
    delay(500)

    assertNull(fixture.stenographer.blockReason())
    assertEquals(1, fixture.storage.finalizeCalls.size)
  }

  @Test
  fun finalizePending_canBeFlushedAfterMatchEnd() = runBlocking {
    val fixture = reportStenographerFixture()
    val runner = player("runner")
    fixture.stenographer.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
    delay(200)
    fixture.stenographer.onReportableEvent(playerDied(runner))

    fixture.storage.failFinalize = true
    fixture.stenographer.onMatchEvent(
      MatchEvent.MatchEnd(
        MatchEngine.MatchState.Finished(
          runner, emptySet(), Clock.System.now(), Clock.System.now(), MatchEngine.MatchOutcome.INCONCLUSIVE,
        ),
      ),
    )
    delay(300)
    assertNotNull(fixture.stenographer.blockReason())

    fixture.storage.failFinalize = false
    val result = fixture.stenographer.manualFlush()
    assertIs<ReportOpResult.Ok>(result)
    assertEquals(1, fixture.storage.finalizeCalls.size)
    assertNull(fixture.stenographer.blockReason())
  }
}
