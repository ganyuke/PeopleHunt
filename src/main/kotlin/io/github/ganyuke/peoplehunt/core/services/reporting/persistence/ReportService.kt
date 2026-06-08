package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.ports.inbound.ReportPort
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.WebReportSerializer
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.fromCompactString
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.toCompactString
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.stenography.ReportStenographer
import java.nio.file.Path
import kotlin.uuid.Uuid

class ReportService(
    private val stenographer: ReportStenographer,
    private val webSerializer: WebReportSerializer,
    private val reportsDir: Path,
) : ReportPort {
    override val blockReason: ReportSessionBlockReason? = stenographer.blockReason()

    override fun manualFlush(callback: (ReportOpResult) -> Unit) {
        stenographer.manualFlush(callback)
    }

    override fun clear(): ReportOpResult = stenographer.clear()

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

    override fun export(matchId: Uuid, callback: (ReportOpResult) -> Unit) {
        if (!reportsDir.resolve("${matchId.toCompactString()}.db").toFile().exists()) {
            callback(ReportOpResult.Err(ReportOpFailure.MATCH_NOT_FOUND))
            return
        }

        webSerializer.export(matchId, callback)
    }

    override fun export(matchId: Uuid, callback: (ReportOpResult) -> Unit) {
        if (!reportsDir.resolve("${matchId.toCompactString()}.db").toFile().exists()) {
            return ReportOpResult.Err(ReportOpFailure.MATCH_NOT_FOUND)
        }
    }
}
