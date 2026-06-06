package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import kotlin.uuid.Uuid

interface ReportInboundPort {
    fun blockReason(): ReportSessionBlockReason?

    suspend fun manualFlush(): ReportOpResult
    fun clear(): ReportOpResult
    fun listExportableMatchIds(): List<Uuid>
    suspend fun export(matchId: Uuid): ReportOpResult
}
