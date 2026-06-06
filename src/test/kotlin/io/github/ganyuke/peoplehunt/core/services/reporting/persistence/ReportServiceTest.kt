package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.ReportJson
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.SqliteStorage
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.WebReportSerializer
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.toCompactString
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.reportStenographerFixture
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

class ReportServiceTest {
  @Test
  fun delegatesSessionGateToStenographer() = runBlocking {
    val fixture = reportStenographerFixture()
    val dir = Files.createTempDirectory("ph-service")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val service = ReportService(
      fixture.stenographer,
      WebReportSerializer(dir, storage, ReportJson.instance),
      dir,
    )

    assertNull(service.blockReason())
    fixture.stenographer.onMatchEvent(MatchEvent.MatchStart(player("runner"), emptySet()))
    delay(50)
    assertEquals(ReportSessionBlockReason.SESSION_ALREADY_ACTIVE, service.blockReason())
  }

  @Test
  fun listExportableMatchIds_findsDbFiles() = runBlocking {
    val dir = Files.createTempDirectory("ph-list")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val fixture = reportStenographerFixture()
    val service = ReportService(
      fixture.stenographer,
      WebReportSerializer(dir, storage, ReportJson.instance),
      dir,
    )
    val matchId = Uuid.random()
    dir.resolve("${matchId.toCompactString()}.db").toFile().writeText("placeholder")

    val ids = service.listExportableMatchIds()
    assertEquals(listOf(matchId), ids)
  }

  @Test
  fun export_returnsNotFoundWhenDbMissing() = runBlocking {
    val dir = Files.createTempDirectory("ph-missing")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val fixture = reportStenographerFixture()
    val service = ReportService(
      fixture.stenographer,
      WebReportSerializer(dir, storage, ReportJson.instance),
      dir,
    )
    val result = service.export(Uuid.random())
    assertIs<ReportOpResult.Err>(result)
    assertEquals(ReportOpFailure.MATCH_NOT_FOUND, result.reason)
  }

  @Test
  fun export_succeedsWhenDatabaseExists() = runBlocking {
    val dir = Files.createTempDirectory("ph-export-ok")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val fixture = reportStenographerFixture()
    val service = ReportService(
      fixture.stenographer,
      WebReportSerializer(dir, storage, ReportJson.instance),
      dir,
    )
    val runner = player("runner")
    val startedAt = kotlin.time.Clock.System.now()
    val matchId = kotlin.uuid.Uuid.random()
    storage.openMatch(
      io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.MatchOpenSession(
        matchId, startedAt, runner, emptyList(),
      ),
    )
    storage.closeActive()

    val result = service.export(matchId)
    assertIs<ReportOpResult.Ok>(result)
    assertTrue(dir.resolve("${matchId.toCompactString()}.json").toFile().exists())
  }

  @Test
  fun clear_delegatesToStenographer() = runBlocking {
    val dir = Files.createTempDirectory("ph-clear")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val fixture = reportStenographerFixture()
    val service = ReportService(
      fixture.stenographer,
      WebReportSerializer(dir, storage, ReportJson.instance),
      dir,
    )
    fixture.stenographer.onMatchEvent(MatchEvent.MatchStart(player("runner"), emptySet()))
    delay(50)

    val result = service.clear()
    assertIs<ReportOpResult.Ok>(result)
    assertNull(service.blockReason())
  }
}
