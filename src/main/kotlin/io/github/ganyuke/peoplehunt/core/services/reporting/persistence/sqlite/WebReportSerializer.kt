package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite

import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.EventFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.serializer
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.uuid.Uuid

class WebReportSerializer(
    private val reportsDir: Path,
    private val storage: SqliteStorage,
    private val json: Json,
) {
    suspend fun export(matchId: Uuid): Path = withContext(Dispatchers.IO) {
        val dbPath = storage.dbPathFor(matchId)
        require(dbPath.toFile().exists()) { "No report database for match $matchId" }
        val report = SqliteReportReader.read(dbPath, json)
        val outPath = reportsDir.resolve("${matchId.toCompactString()}.json")
        outPath.writeText(json.encodeToString(JsonElement.serializer(), buildDocument(report)))
        outPath
    }

    private fun buildDocument(report: PersistedMatchReport): JsonElement {
        val frames = buildList {
            report.snapshotFrames.forEach { add(it to "snapshots") }
            report.projectileFrames.forEach { add(it to "projectiles") }
            report.eventFrames.forEach { add(it to "events") }
        }.sortedBy { (frame, _) -> frame.tick }

        return buildJsonObject {
            put("matchId", report.matchId.toCompactString())
            put("startedAt", report.startedAt.toEpochMilliseconds())
            report.endedAt?.let { put("endedAt", it.toEpochMilliseconds()) }
            report.durationTicks?.let { put("durationTicks", it) }
            report.outcome?.let { put("outcome", it.name) }
            putJsonObject("runner") {
                put("uuid", report.runner.uuid.toCompactString())
                put("name", report.runner.name)
            }
            putJsonArray("hunters") {
                report.hunters.forEach { hunter ->
                    add(
                        buildJsonObject {
                            put("uuid", hunter.uuid.toCompactString())
                            put("name", hunter.name)
                        },
                    )
                }
            }
            putJsonArray("frames") {
                frames.forEach { (frame, category) ->
                    add(
                        buildJsonObject {
                            put("tick", frame.tick)
                            put("occurredAt", frame.occurredAt.toEpochMilliseconds())
                            put("category", category)
                            put("type", payloadTypeName(frame.payload))
                            put("payload", json.encodeToJsonElement(ReportablePayload.serializer(), frame.payload))
                        },
                    )
                }
            }
        }
    }

    private fun payloadTypeName(payload: ReportablePayload): String =
        json.serializersModule.serializer(payload::class.java).descriptor.serialName
}
