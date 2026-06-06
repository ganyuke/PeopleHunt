package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite

import io.github.ganyuke.peoplehunt.core.services.core.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportStorage
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.FrameBatch
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.MatchOpenSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlin.time.Instant
import kotlin.uuid.Uuid

class SqliteStorage(
    private val reportsDir: Path,
    private val json: Json,
) : ReportStorage {
    private var activeMatchId: Uuid? = null
    private var connection: Connection? = null

    fun verifyStorage() {
        reportsDir.toFile().mkdirs()
        val healthPath = reportsDir.resolve(".healthcheck.db")
        val healthFile = healthPath.toFile()
        if (healthFile.exists()) healthFile.delete()
        try {
            DriverManager.getConnection("jdbc:sqlite:$healthPath").use { conn ->
                writeJsonbHealthProbe(conn)
                requireJsonbHealthProbe(conn)
            }
        } finally {
            if (healthFile.exists()) healthFile.delete()
        }
    }

    companion object {
        internal fun writeJsonbHealthProbe(conn: Connection) {
            conn.createStatement().use { stmt ->
                stmt.execute("PRAGMA foreign_keys = ON")
                stmt.execute("CREATE TABLE t (data JSONB)")
            }
            conn.prepareStatement("INSERT INTO t VALUES (jsonb('{\"probe\":true}'))").use { it.executeUpdate() }
        }

        internal fun requireJsonbHealthProbe(conn: Connection) {
            conn.createStatement().executeQuery("SELECT json(data) FROM t").use { rs ->
                check(rs.next()) { "health check SELECT returned no rows" }
                val value = rs.getString(1)
                check(value != null && value.contains("probe")) { "JSONB round-trip failed: $value" }
            }
        }
    }

    override suspend fun openMatch(session: MatchOpenSession) = withContext(Dispatchers.IO) {
        closeActive()
        val conn = openConnection(dbPathFor(session.matchId))
        activeMatchId = session.matchId
        connection = conn
        initializeSchema(conn)
        insertPlayers(conn, session)
        insertMatchInfo(conn, session)
    }

    override suspend fun appendFlush(matchId: Uuid, batch: FrameBatch, flushTime: Instant) {
        if (batch.isEmpty()) return
        withContext(Dispatchers.IO) {
        val range = batch.tickRange() ?: return@withContext
        val conn = connectionFor(matchId)
        val snapshotsBlob = batch.snapshots.takeIf { it.isNotEmpty() }?.let { FlushBatchCodec.encode(it, json) }
        val projectilesBlob = batch.projectiles.takeIf { it.isNotEmpty() }?.let { FlushBatchCodec.encode(it, json) }
        val eventsBlob = batch.events.takeIf { it.isNotEmpty() }?.let { FlushBatchCodec.encode(it, json) }

        conn.prepareStatement(
            """
            INSERT INTO flush_batches (
                flush_time, tick_min, tick_max,
                snapshot_count, projectile_count, event_count,
                snapshots, projectiles, events
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setLong(1, flushTime.toEpochMilliseconds())
            stmt.setInt(2, range.first)
            stmt.setInt(3, range.last)
            stmt.setInt(4, batch.snapshots.size)
            stmt.setInt(5, batch.projectiles.size)
            stmt.setInt(6, batch.events.size)
            setBlob(stmt, 7, snapshotsBlob)
            setBlob(stmt, 8, projectilesBlob)
            setBlob(stmt, 9, eventsBlob)
            stmt.executeUpdate()
        }
        }
    }

    override suspend fun finalizeMatch(
        matchId: Uuid,
        endedAt: Instant,
        outcome: MatchEngine.MatchOutcome,
        durationTicks: Int,
    ) = withContext(Dispatchers.IO) {
        val conn = connectionFor(matchId)
        conn.prepareStatement(
            "UPDATE match_info SET ended_at = ?, duration_ticks = ?, outcome = ? WHERE id = ?",
        ).use { stmt ->
            stmt.setLong(1, endedAt.toEpochMilliseconds())
            stmt.setInt(2, durationTicks)
            stmt.setString(3, outcome.name)
            stmt.setString(4, matchId.toCompactString())
            stmt.executeUpdate()
        }
        closeActive()
    }

    override fun closeActive() {
        connection?.close()
        connection = null
        activeMatchId = null
    }

    fun dbPathFor(matchId: Uuid): Path = reportsDir.resolve("${matchId.toCompactString()}.db")

    private fun connectionFor(matchId: Uuid): Connection {
        val existing = connection
        if (existing != null && activeMatchId == matchId) return existing
        closeActive()
        val conn = openConnection(dbPathFor(matchId))
        activeMatchId = matchId
        connection = conn
        initializeSchema(conn)
        return conn
    }

    private fun openConnection(dbPath: Path): Connection {
        dbPath.parent?.toFile()?.mkdirs()
        val conn = DriverManager.getConnection("jdbc:sqlite:$dbPath")
        conn.createStatement().use { stmt ->
            stmt.execute("PRAGMA busy_timeout = 10000")
            stmt.execute("PRAGMA journal_mode=WAL")
            stmt.execute("PRAGMA foreign_keys = ON")
        }
        return conn
    }

    private fun initializeSchema(conn: Connection) {
        conn.createStatement().use { stmt ->
            SqliteSchema.DDL.forEach { stmt.execute(it) }
        }
    }

    private fun insertPlayers(conn: Connection, session: MatchOpenSession) {
        conn.prepareStatement("INSERT OR REPLACE INTO players (uuid, name, roles) VALUES (?, ?, ?)").use { stmt ->
            fun insert(player: io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer, role: String) {
                stmt.setString(1, player.uuid.toCompactString())
                stmt.setString(2, player.name)
                stmt.setString(3, role)
                stmt.executeUpdate()
            }
            insert(session.runner, "runner")
            session.hunters.forEach { insert(it, "hunter") }
        }
    }

    private fun insertMatchInfo(conn: Connection, session: MatchOpenSession) {
        conn.prepareStatement(
            """
            INSERT OR REPLACE INTO match_info (id, started_at, ended_at, duration_ticks, outcome, runner_uuid)
            VALUES (?, ?, NULL, NULL, NULL, ?)
            """.trimIndent(),
        ).use { stmt ->
            stmt.setString(1, session.matchId.toCompactString())
            stmt.setLong(2, session.startedAt.toEpochMilliseconds())
            stmt.setString(3, session.runner.uuid.toCompactString())
            stmt.executeUpdate()
        }
    }

    private fun setBlob(stmt: java.sql.PreparedStatement, index: Int, bytes: ByteArray?) {
        if (bytes == null) stmt.setNull(index, java.sql.Types.BLOB)
        else stmt.setBytes(index, bytes)
    }
}
