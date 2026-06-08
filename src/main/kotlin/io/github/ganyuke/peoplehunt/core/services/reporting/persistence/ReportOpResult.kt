package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

sealed interface ReportOpResult {
    data class Ok(val message: String? = null) : ReportOpResult
    data class Err(val reason: ReportOpFailure, val cause: Throwable? = null) : ReportOpResult
}

enum class ReportOpFailure {
    NO_OPEN_SESSION,
    IO_IN_PROGRESS,
    DB_FAILED_TO_OPEN,
    DB_FAILED_TO_FINALIZE,
    NOTHING_TO_FLUSH,
    WRITE_FAILED,
    MATCH_NOT_FOUND,
    EXPORT_FAILED,
    INVALID_STATE
}
