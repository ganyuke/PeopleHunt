package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite

object SqliteSchema {
    val DDL = listOf(
        """
        CREATE TABLE IF NOT EXISTS players (
            uuid TEXT PRIMARY KEY,
            name TEXT NOT NULL,
            roles TEXT NOT NULL
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS match_info (
            id TEXT PRIMARY KEY,
            started_at INTEGER NOT NULL,
            ended_at INTEGER,
            duration_ticks INTEGER,
            outcome TEXT,
            runner_uuid TEXT NOT NULL REFERENCES players(uuid)
        )
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS flush_batches (
            batch_seq INTEGER PRIMARY KEY AUTOINCREMENT,
            flush_time INTEGER NOT NULL,
            tick_min INTEGER NOT NULL,
            tick_max INTEGER NOT NULL,
            snapshot_count INTEGER NOT NULL,
            projectile_count INTEGER NOT NULL,
            event_count INTEGER NOT NULL,
            snapshots BLOB,
            projectiles BLOB,
            events BLOB
        )
        """.trimIndent(),
    )
}
