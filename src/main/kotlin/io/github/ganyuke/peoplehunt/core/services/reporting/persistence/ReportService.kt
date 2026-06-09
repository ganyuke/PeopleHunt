package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.events.models.MatchPlayer
import io.github.ganyuke.peoplehunt.core.ports.inbound.ReportPort
import io.github.ganyuke.peoplehunt.core.services.reporting.highlighter.CombatStatsTracker
import io.github.ganyuke.peoplehunt.core.services.reporting.highlighter.EventHighlighter
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.exporter.WebReportSerializer
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportOpFailure
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportOpResult
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.stenography.ReportStartRejectReason
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.stenography.ReportStenographer
import io.github.ganyuke.peoplehunt.core.utils.fromCompactString
import io.github.ganyuke.peoplehunt.core.utils.toCompactString
import java.nio.file.Path
import kotlin.uuid.Uuid

class ReportService(
    private val stenographer: ReportStenographer,
    private val webSerializer: WebReportSerializer,
    private val reportsDir: Path,
    highlighter: EventHighlighter
) : ReportPort {
    override val blockReason: ReportStartRejectReason? get() = stenographer.blockReason

    override val participantStats: List<Pair<MatchPlayer, CombatStatsTracker.PlayerStats>> = highlighter.participantStats

    override fun flush(callback: (ReportOpResult) -> Unit) {
        stenographer.flush(callback)
    }

    override fun discard(): ReportOpResult = stenographer.discard()

    override fun fetchMatchIdList(): List<Uuid> {
        val dir = reportsDir.toFile()
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { file -> file.isFile && file.name.endsWith(".db") }
            ?.mapNotNull { file ->
                val id = file.name.removeSuffix(".db")
                runCatching { Uuid.fromCompactString(id) }.getOrNull()
            }
            ?.sortedBy { it.toCompactString() }
            ?: emptyList()
    }

    override fun export(matchId: Uuid, callback: (ReportOpResult, Path?) -> Unit) {
        if (!reportsDir.resolve("${matchId.toCompactString()}.db").toFile().exists()) {
            callback(ReportOpResult.Err(ReportOpFailure.MATCH_NOT_FOUND), null)
            return
        }

        webSerializer.export(matchId, callback)
    }
}
