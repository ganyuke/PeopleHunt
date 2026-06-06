package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.EventFrame
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.FrameBatch
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.MatchOpenSession
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.ReportJson
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.SqliteStorage
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.WebReportSerializer
import io.github.ganyuke.peoplehunt.core.testutil.FakeLogger
import io.github.ganyuke.peoplehunt.core.testutil.FakeScheduler
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.playerJoined
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ReportExportHandlerTest {
  @Test
  fun reportPersisted_triggersAutoExport() = runBlocking {
    val dir = Files.createTempDirectory("ph-auto-export")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val serializer = WebReportSerializer(dir, storage, ReportJson.instance)
    val scheduler = FakeScheduler()
    val logger = FakeLogger()
    val bus = MatchEventBus()
    val handler = ReportExportHandler(serializer, scheduler, logger, bus)

    val matchId = Uuid.random()
    val runner = player("runner")
    val startedAt = Clock.System.now()
    storage.openMatch(MatchOpenSession(matchId, startedAt, runner, emptyList()))
    storage.appendFlush(
      matchId,
      FrameBatch(emptyList(), emptyList(), listOf(EventFrame(1, startedAt, playerJoined(runner).payload))),
      startedAt,
    )

    storage.closeActive()
    handler.onMatchEvent(MatchEvent.ReportPersisted(matchId))
    delay(200)

    assertEquals(matchId, handler.lastPersistedMatchId)
    val jsonPath = dir.resolve("${matchId.toString().replace("-", "")}.json")
    assertTrue(jsonPath.toFile().exists())
    assertTrue(logger.infoMessages.any { it.contains("Exported web report") })
  }

  @Test
  fun reportPersisted_logsAndNotifiesOnExportFailure() = runBlocking {
    val dir = Files.createTempDirectory("ph-export-fail")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val serializer = WebReportSerializer(dir, storage, ReportJson.instance)
    val scheduler = FakeScheduler()
    val logger = FakeLogger()
    val bus = MatchEventBus()
    val notifications = mutableListOf<MatchEvent>()
    bus.register { notifications += it }
    val handler = ReportExportHandler(serializer, scheduler, logger, bus)

    handler.onMatchEvent(MatchEvent.ReportPersisted(Uuid.random()))
    delay(200)

    assertTrue(logger.errorMessages.isNotEmpty())
    assertTrue(notifications.any { it is MatchEvent.OperatorNotification })
    handler.shutdown()
  }
}
