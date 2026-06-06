package io.github.ganyuke.peoplehunt.core.services.reporting.persistence

enum class ReportSessionState {
    CLOSED,
    RECORDING,
    OPEN_FAILED,
    FINALIZE_PENDING,
}
