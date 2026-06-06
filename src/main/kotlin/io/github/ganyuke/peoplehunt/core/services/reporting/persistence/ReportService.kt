package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.WebReportSerializer
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.fromCompactString
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.sqlite.toCompactString
import java.nio.file.Path
import kotlin.uuid.Uuid

class ReportService(
    private val stenographer: ReportStenographer,
    private val webSerializer: WebReportSerializer,
    private val reportsDir: Path,
) : ReportInboundPort {
    override fun blockReason(): ReportSessionBlockReason? = stenographer.blockReason()

    override suspend fun manualFlush(): ReportOpResult = stenographer.manualFlush()

    override fun clear(): ReportOpResult = stenographer.clear()

    override fun listExportableMatchIds(): List<Uuid> {
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

    override suspend fun export(matchId: Uuid): ReportOpResult {
        if (!reportsDir.resolve("${matchId.toCompactString()}.db").toFile().exists()) {
            return ReportOpResult.Err(ReportOpFailure.MATCH_NOT_FOUND)
        }
        return runCatching { webSerializer.export(matchId) }
            .fold(
                onSuccess = { path ->
                    ReportOpResult.Ok("Exported report to ${path.fileName}")
                },
                onFailure = { cause ->
                    ReportOpResult.Err(ReportOpFailure.EXPORT_FAILED, cause)
                },
            )
    }
}
