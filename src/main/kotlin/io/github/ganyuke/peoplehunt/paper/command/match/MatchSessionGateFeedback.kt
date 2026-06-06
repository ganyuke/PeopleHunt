package io.github.ganyuke.peoplehunt.paper.command.match

import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportSessionBlockReason

object MatchSessionGateFeedback {
    fun blockMessage(reason: ReportSessionBlockReason): String = when (reason) {
        ReportSessionBlockReason.SESSION_ALREADY_ACTIVE -> "A report session is already active."
        ReportSessionBlockReason.DATABASE_OPEN_FAILED ->
            "Report database failed to open — run /ph report flush or /ph report clear before starting a new match."
        ReportSessionBlockReason.FINALIZE_PENDING ->
            "Report data from the last match was not saved — run /ph report flush or /ph report clear before starting a new match."
    }
}
