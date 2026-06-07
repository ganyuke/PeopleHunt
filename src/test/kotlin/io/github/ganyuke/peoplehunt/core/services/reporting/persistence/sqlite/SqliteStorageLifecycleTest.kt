package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite

import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchOutcome
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.EventFrame
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.FrameBatch
import io.github.ganyuke.peoplehunt.core.testutil.player
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid
import kotlinx.coroutines.runBlocking

class SqliteStorageLifecycleTest {
  @Test
  fun openAppendFinalize_persistsFlushTimeRolesAndForeignKey() = runBlocking {
    val dir = Files.createTempDirectory("ph-reports")
    val storage = SqliteStorage(dir, ReportJson.instance)
    val matchId = Uuid.random()
    val runner = player("runner")
    val hunter = player("hunter")
    val startedAt = Clock.System.now()
    storage.openMatch(MatchOpenSession(matchId, startedAt, runner, listOf(hunter)))

    val flushTime = Instant.fromEpochMilliseconds(42_000L)
    val batch = FrameBatch(
      projectiles = emptyList(),
      snapshots = emptyList(),
      events = listOf(
        EventFrame(5, startedAt, ReportablePayload.PlayerJoined(runner)),
      ),
    )
    storage.appendFlush(matchId, batch, flushTime)

    storage.closeActive()
    DriverManager.getConnection("jdbc:sqlite:${storage.dbPathFor(matchId)}").use { conn ->
      conn.prepareStatement("SELECT roles FROM players WHERE uuid = ?").use { stmt ->
        stmt.setString(1, runner.uuid.toCompactString())
        stmt.executeQuery().use { rs ->
          assertTrue(rs.next())
          assertEquals("runner", rs.getString("roles"))
        }
      }
      conn.prepareStatement("SELECT roles FROM players WHERE uuid = ?").use { stmt ->
        stmt.setString(1, hunter.uuid.toCompactString())
        stmt.executeQuery().use { rs ->
          assertTrue(rs.next())
          assertEquals("hunter", rs.getString("roles"))
        }
      }
      conn.prepareStatement("SELECT flush_time FROM flush_batches").use { stmt ->
        stmt.executeQuery().use { rs ->
          assertTrue(rs.next())
          assertEquals(42_000L, rs.getLong("flush_time"))
        }
      }
      conn.prepareStatement("PRAGMA foreign_key_check(match_info)").use { stmt ->
        stmt.executeQuery().use { rs ->
          assertTrue(!rs.next(), "match_info runner_uuid FK should be valid")
        }
      }
    }

    storage.finalizeMatch(matchId, startedAt, MatchOutcome.RUNNER_VICTORY, 5)
    val report = SqliteReportReader.read(storage.dbPathFor(matchId), ReportJson.instance)
    assertEquals(1, report.eventFrames.size)
    assertEquals(MatchOutcome.RUNNER_VICTORY, report.outcome)
  }
}
