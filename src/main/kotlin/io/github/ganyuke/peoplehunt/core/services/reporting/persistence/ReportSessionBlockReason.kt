package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

enum class ReportSessionBlockReason {
    SESSION_ALREADY_ACTIVE,
    DATABASE_OPEN_FAILED,
    FINALIZE_PENDING,
}
