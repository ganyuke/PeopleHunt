package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite

import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchOutcome
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.EventFrame
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.FrameBatch
import io.github.ganyuke.peoplehunt.core.testutil.player
import io.github.ganyuke.peoplehunt.core.testutil.playerJoined
import io.github.ganyuke.peoplehunt.core.testutil.playerSnapshotChanged
import io.github.ganyuke.peoplehunt.core.testutil.projectileLaunched
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking

class SqliteStorageAppendTest {
  @Test
  fun appendFlush_emptyBatchIsNoOp() = runBlocking {
    val dir = Files.createTempDirectory("ph-empty-flush")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val matchId = Uuid.random()
    storage.openMatch(MatchOpenSession(matchId, Clock.System.now(), player("runner"), emptyList()))
    storage.appendFlush(matchId, FrameBatch(emptyList(), emptyList(), emptyList()), Instant.fromEpochMilliseconds(1))
    storage.closeActive()

    val report = SqliteReportReader.read(storage.dbPathFor(matchId), ReportJson.instance)
    assertEquals(0, report.eventFrames.size + report.projectileFrames.size + report.snapshotFrames.size)
  }

  @Test
  fun appendFlush_multipleBatchesWithAllCategories() = runBlocking {
    val dir = Files.createTempDirectory("ph-multi-flush")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val matchId = Uuid.random()
    val runner = player("runner")
    val hunter = player("hunter")
    val startedAt = Clock.System.now()
    storage.openMatch(MatchOpenSession(matchId, startedAt, runner, listOf(hunter)))

    storage.appendFlush(
      matchId,
      FrameBatch(
        projectiles = listOf(EventFrame(1, startedAt, projectileLaunched(shooter = runner).payload)),
        snapshots = listOf(EventFrame(2, startedAt, playerSnapshotChanged(runner).payload)),
        events = listOf(EventFrame(3, startedAt, playerJoined(runner).payload)),
      ),
      startedAt,
    )
    storage.appendFlush(
      matchId,
      FrameBatch(
        projectiles = emptyList(),
        snapshots = emptyList(),
        events = listOf(EventFrame(4, startedAt, ReportablePayload.PlayerQuit(runner, "QUIT"))),
      ),
      Instant.fromEpochMilliseconds(startedAt.toEpochMilliseconds() + 1_000),
    )
    storage.finalizeMatch(
      matchId,
      startedAt,
      MatchOutcome.INCONCLUSIVE,
      4,
    )

    val report = SqliteReportReader.read(storage.dbPathFor(matchId), ReportJson.instance)
    assertEquals(1, report.projectileFrames.size)
    assertEquals(1, report.snapshotFrames.size)
    assertEquals(2, report.eventFrames.size)
    assertEquals(1, report.hunters.size)
    assertEquals(hunter.name, report.hunters.single().name)
  }
}
