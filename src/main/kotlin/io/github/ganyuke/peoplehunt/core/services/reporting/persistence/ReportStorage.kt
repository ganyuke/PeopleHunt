package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.models.ReportDocument

fun interface ReportStorage {
    suspend fun write(doc: ReportDocument)
}