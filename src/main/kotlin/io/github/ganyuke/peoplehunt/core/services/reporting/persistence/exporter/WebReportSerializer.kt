package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.exporter

import io.github.ganyuke.peoplehunt.core.events.ReportablePayload
import io.github.ganyuke.peoplehunt.core.ports.outbound.SchedulerPort
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.PersistedMatchReport
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportOpFailure
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportOpResult
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.SqliteStorage
import io.github.ganyuke.peoplehunt.core.utils.toCompactString
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlinx.serialization.serializer
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.uuid.Uuid

class WebReportSerializer(
    private val reportsDir: Path,
    private val storage: SqliteStorage,
    private val json: Json,
    private val scheduler: SchedulerPort
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun export(matchId: Uuid, callback: (ReportOpResult, Path?) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val result = runCatching {
                // request persisted model from JSON
                val report = storage.readMatch(matchId)

                // export report data as a JSON file
                val outPath = reportsDir.resolve("${matchId.toCompactString()}.json")
                outPath.writeText(json.encodeToString(JsonElement.serializer(), buildDocument(report)))
                outPath
            }

            scheduler.runOnMainThread {
                if (result.isSuccess) {
                    callback(ReportOpResult.Ok("Exported report successfully."), result.getOrNull())
                } else {
                    callback(ReportOpResult.Err(ReportOpFailure.EXPORT_FAILED, result.exceptionOrNull()), null)
                }
            }
        }
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

    fun shutdown() {
        scope.cancel()
    }
}
