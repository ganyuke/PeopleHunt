package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite

import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchOutcome
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.exporter.WebReportSerializer
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.EventFrame
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.FrameBatch
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.projectileLaunched
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking

class WebReportSerializerTest {
  @Test
  fun export_writesJsonFromDecompressedBatches() = runBlocking {
    val dir = Files.createTempDirectory("ph-export")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val serializer = WebReportSerializer(dir, storage, ReportJson.instance)
    val matchId = Uuid.random()
    val runner = player("runner")
    val startedAt = Clock.System.now()
    storage.openMatch(MatchOpenSession(matchId, startedAt, runner, emptyList()))
    storage.appendFlush(
      matchId,
      FrameBatch(
        projectiles = emptyList(),
        snapshots = emptyList(),
        events = listOf(EventFrame(3, startedAt, ReportablePayload.PlayerJoined(runner))),
      ),
      startedAt,
    )

    storage.closeActive()
    val path = serializer.export(matchId)
    val json = path.toFile().readText()
    assertTrue(json.contains("\"matchId\""))
    assertTrue(json.contains(runner.name))
    assertTrue(json.contains("PlayerJoined"))
    assertTrue(json.contains("\"frames\""))
  }

  @Test
  fun export_includesFinalizedMetadataAndHunters() = runBlocking {
    val dir = Files.createTempDirectory("ph-export-meta")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val serializer = WebReportSerializer(dir, storage, ReportJson.instance)
    val matchId = Uuid.random()
    val runner = player("runner")
    val hunter = player("hunter")
    val startedAt = Clock.System.now()
    storage.openMatch(MatchOpenSession(matchId, startedAt, runner, listOf(hunter)))
    storage.appendFlush(
      matchId,
      FrameBatch(
        projectiles = listOf(EventFrame(1, startedAt, projectileLaunched(shooter = runner).payload)),
        snapshots = emptyList(),
        events = emptyList(),
      ),
      startedAt,
    )
    storage.finalizeMatch(matchId, startedAt, MatchOutcome.RUNNER_VICTORY, 1)
    storage.closeActive()

    val json = serializer.export(matchId).toFile().readText()
    assertTrue(json.contains("\"endedAt\""))
    assertTrue(json.contains("\"outcome\""))
    assertTrue(json.contains("RUNNER_VICTORY"))
    assertTrue(json.contains(hunter.name))
    assertTrue(json.contains("\"category\":\"projectiles\""))
  }
}
