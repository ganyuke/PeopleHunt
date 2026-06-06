package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.ports.LoggerPort
import io.github.ganyuke.peoplehunt.core.ports.SchedulerPort
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.WebReportSerializer
import kotlin.uuid.Uuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ReportExportHandler(
    private val webSerializer: WebReportSerializer,
    private val scheduler: SchedulerPort,
    private val logger: LoggerPort,
    private val outbound: MatchEventBus,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    var lastPersistedMatchId: Uuid? = null
        private set

    fun onMatchEvent(event: MatchEvent) {
        when (event) {
            is MatchEvent.ReportPersisted -> {
                lastPersistedMatchId = event.matchId
                scope.launch {
                    runCatching { webSerializer.export(event.matchId) }
                        .onSuccess { path ->
                            scheduler.runOnMainThread {
                                logger.info("Exported web report for match ${event.matchId} to $path")
                            }
                        }
                        .onFailure { cause ->
                            scheduler.runOnMainThread {
                                logger.error("JSON export failed for match ${event.matchId}", cause)
                                outbound.post(
                                    MatchEvent.OperatorNotification(
                                        "JSON export failed for match ${event.matchId}: ${cause.message ?: "Unknown error"}",
                                    ),
                                )
                            }
                        }
                }
            }

            else -> {}
        }
    }

    fun shutdown() = scope.cancel()
}
