package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchOutcome
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.EventFrame
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.time.Instant
import kotlin.uuid.Uuid

data class PersistedMatchReport(
    val matchId: Uuid,
    val startedAt: Instant,
    val endedAt: Instant?,
    val durationTicks: Int?,
    val outcome: MatchOutcome?,
    val runner: MatchPlayer,
    val hunters: List<MatchPlayer>,
    val snapshotFrames: List<EventFrame>,
    val projectileFrames: List<EventFrame>,
    val eventFrames: List<EventFrame>,
)

object SqliteReportReader {
    fun read(dbPath: Path, json: Json): PersistedMatchReport {
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
            val matchInfo = conn.prepareStatement(
                """
                SELECT m.id, m.started_at, m.ended_at, m.duration_ticks, m.outcome, m.runner_uuid,
                       r.name AS runner_name
                FROM match_info m
                JOIN players r ON r.uuid = m.runner_uuid
                """.trimIndent(),
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    check(rs.next()) { "match_info missing in $dbPath" }
                    MatchInfoRow(
                        id = Uuid.fromCompactString(rs.getString("id")),
                        startedAt = Instant.fromEpochMilliseconds(rs.getLong("started_at")),
                        endedAt = (rs.getObject("ended_at") as? Number)?.toLong()
                            ?.let(Instant::fromEpochMilliseconds),
                        durationTicks = rs.getObject("duration_ticks") as? Int,
                        outcome = rs.getString("outcome")?.let { MatchOutcome.valueOf(it) },
                        runner = MatchPlayer(
                            Uuid.fromCompactString(rs.getString("runner_uuid")),
                            rs.getString("runner_name"),
                        ),
                    )
                }
            }

            val hunters = conn.prepareStatement(
                "SELECT uuid, name FROM players WHERE roles = 'hunter'",
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    buildList {
                        while (rs.next()) {
                            add(MatchPlayer(Uuid.fromCompactString(rs.getString("uuid")), rs.getString("name")))
                        }
                    }
                }
            }

            val snapshots = mutableListOf<EventFrame>()
            val projectiles = mutableListOf<EventFrame>()
            val events = mutableListOf<EventFrame>()

            conn.prepareStatement(
                "SELECT snapshots, projectiles, events FROM flush_batches ORDER BY batch_seq",
            ).use { stmt ->
                stmt.executeQuery().use { rs ->
                    while (rs.next()) {
                        rs.getBytes("snapshots")?.let { snapshots += FlushBatchCodec.decode(it, json) }
                        rs.getBytes("projectiles")?.let { projectiles += FlushBatchCodec.decode(it, json) }
                        rs.getBytes("events")?.let { events += FlushBatchCodec.decode(it, json) }
                    }
                }
            }

            return PersistedMatchReport(
                matchId = matchInfo.id,
                startedAt = matchInfo.startedAt,
                endedAt = matchInfo.endedAt,
                durationTicks = matchInfo.durationTicks,
                outcome = matchInfo.outcome,
                runner = matchInfo.runner,
                hunters = hunters,
                snapshotFrames = snapshots,
                projectileFrames = projectiles,
                eventFrames = events,
            )
        }
    }

    private data class MatchInfoRow(
        val id: Uuid,
        val startedAt: Instant,
        val endedAt: Instant?,
        val durationTicks: Int?,
        val outcome: MatchOutcome?,
        val runner: MatchPlayer,
    )
}
