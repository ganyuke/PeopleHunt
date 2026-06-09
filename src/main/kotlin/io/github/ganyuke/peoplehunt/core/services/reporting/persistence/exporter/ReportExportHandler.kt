package io.github.ganyuke.peoplehunt.core.services.reporting.persistence.exporter

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.ports.outbound.LoggerPort
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportOpResult
import kotlin.uuid.Uuid

/**
 * Automatically exports the JSON for a match upon completion of the match's report.
 */
class ReportExportHandler(
    private val webSerializer: WebReportSerializer,
    private val logger: LoggerPort,
    private val outbound: MatchEventBus
) {
    var lastPersistedMatchId: Uuid? = null
        private set

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.ReportPersisted -> {
                lastPersistedMatchId = event.matchId
                webSerializer.export(event.matchId) { result, path ->
                    when (result) {
                        is ReportOpResult.Ok -> logger.info("Exported web report for match ${event.matchId} to $path")
                        is ReportOpResult.Err -> {
                            logger.error("JSON export failed for match ${event.matchId}", result.cause)
                            outbound.post(
                                MatchEvent.OperatorNotification(
                                    "JSON export failed for match ${event.matchId}: ${result.cause?.message ?: "Unknown error"}",
                                ),
                            )
                        }
                    }
                }
            }
            else -> {}
        }
    }
}