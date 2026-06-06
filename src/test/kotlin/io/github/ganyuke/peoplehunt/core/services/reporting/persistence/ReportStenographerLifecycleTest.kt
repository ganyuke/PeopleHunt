package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.ReportJson
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.SqliteReportReader
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.SqliteStorage
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.fromCompactString
import io.github.ganyuke.peoplehunt.core.testutil.FakeLogger
import io.github.ganyuke.peoplehunt.core.testutil.FakeScheduler
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.playerJoined
import io.github.ganyuke.peoplehunt.core.testutil.playerSnapshotChanged
import io.github.ganyuke.peoplehunt.core.testutil.projectileLaunched
import io.github.ganyuke.peoplehunt.core.testutil.testPhConfig
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ReportStenographerLifecycleTest {
  @Test
  fun fullMatchLifecycle_persistsAllBufferTypes() = runBlocking {
    val dir = Files.createTempDirectory("ph-lifecycle")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val bus = MatchEventBus()
    val events = mutableListOf<MatchEvent>()
    bus.register { events += it }
    val stenographer = ReportStenographer(
      bus,
      FakeScheduler(),
      FakeLogger(),
      storage,
      testPhConfig(reportFlushInterval = kotlin.time.Duration.ZERO),
    )

    val runner = player("runner")
    stenographer.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
    delay(200)

    stenographer.onReportableEvent(playerJoined(runner))
    stenographer.onReportableEvent(projectileLaunched(shooter = runner))
    stenographer.onReportableEvent(playerSnapshotChanged(runner))

    val endedAt = Clock.System.now()
    stenographer.onMatchEvent(
      MatchEvent.MatchEnd(
        MatchEngine.MatchState.Finished(
          runner, emptySet(), endedAt, endedAt, MatchEngine.MatchOutcome.RUNNER_VICTORY,
        ),
      ),
    )
    delay(500)

    assertNull(stenographer.blockReason())
    val persisted = events.filterIsInstance<MatchEvent.ReportPersisted>()
    assertEquals(1, persisted.size)

    storage.closeActive()
    val report = SqliteReportReader.read(storage.dbPathFor(persisted.single().matchId), ReportJson.instance)
    assertEquals(1, report.eventFrames.size)
    assertEquals(1, report.projectileFrames.size)
    assertEquals(1, report.snapshotFrames.size)
    assertEquals(MatchEngine.MatchOutcome.RUNNER_VICTORY, report.outcome)
    stenographer.shutdown()
  }

  @Test
  fun manualFlush_withNoBuffers_returnsNothingToFlush() = runBlocking {
    val fixture = io.github.ganyuke.peoplehunt.core.testutil.reportStenographerFixture()
    fixture.stenographer.onMatchEvent(MatchEvent.MatchStart(player("runner"), emptySet()))
    delay(200)
    val result = fixture.stenographer.manualFlush()
    assertIs<ReportOpResult.Err>(result)
    assertEquals(ReportOpFailure.NOTHING_TO_FLUSH, result.reason)
    fixture.stenographer.shutdown()
  }

  @Test
  fun manualFlush_whenClosed_returnsNoOpenSession() = runBlocking {
    val fixture = io.github.ganyuke.peoplehunt.core.testutil.reportStenographerFixture()
    val result = fixture.stenographer.manualFlush()
    assertIs<ReportOpResult.Err>(result)
    assertEquals(ReportOpFailure.NO_OPEN_SESSION, result.reason)
  }

  @Test
  fun autoFlush_schedulerWritesBatches() = runBlocking {
    val dir = Files.createTempDirectory("ph-autoflush")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val stenographer = ReportStenographer(
      MatchEventBus(),
      FakeScheduler(),
      FakeLogger(),
      storage,
      testPhConfig(reportFlushInterval = 30.milliseconds),
    )
    try {
      val runner = player("runner")
      stenographer.onMatchEvent(MatchEvent.MatchStart(runner, emptySet()))
      delay(200)
      stenographer.onReportableEvent(playerJoined(runner))
      delay(150)
      storage.closeActive()
      val dbs = dir.toFile().listFiles { f -> f.name.endsWith(".db") }
      assertTrue(dbs?.isNotEmpty() == true)
      val matchId = Uuid.fromCompactString(dbs!!.single().name.removeSuffix(".db"))
      val report = SqliteReportReader.read(storage.dbPathFor(matchId), ReportJson.instance)
      assertTrue(report.eventFrames.isNotEmpty())
    } finally {
      stenographer.shutdown()
    }
  }

  @Test
  fun reportError_postsOperatorNotification() = runBlocking {
    val fixture = io.github.ganyuke.peoplehunt.core.testutil.reportStenographerFixture()
    val notifications = mutableListOf<MatchEvent>()
    fixture.bus.register { notifications += it }
    fixture.stenographer.reportError(RuntimeException("disk full"), "log", "operator")
    assertTrue(notifications.any { it is MatchEvent.OperatorNotification && it.message.contains("disk full") })
    fixture.stenographer.shutdown()
  }
}
