package io.github.ganyuke.peoplehunt.paper.command.report

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.ports.LoggerPort
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportOpFailure
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportOpResult
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object ReportCommandFeedback {
    fun deliver(
        source: CommandSourceStack,
        logger: LoggerPort,
        outbound: MatchEventBus,
        result: ReportOpResult,
    ) {
        when (result) {
            is ReportOpResult.Ok -> {
                result.message?.let { logger.info(it) }
                source.sender.sendMessage(
                    Component.text(result.message ?: "Report command succeeded.", NamedTextColor.GREEN),
                )
            }

            is ReportOpResult.Err -> {
                val text = failureText(result.reason)
                if (result.reason == ReportOpFailure.WRITE_FAILED || result.reason == ReportOpFailure.EXPORT_FAILED) {
                    logger.error(text, result.cause)
                    outbound.post(MatchEvent.OperatorNotification("$text: ${result.cause?.message ?: "Unknown error"}"))
                }
                source.sender.sendMessage(Component.text(text, NamedTextColor.RED))
            }
        }
    }

    fun failureText(reason: ReportOpFailure): String = when (reason) {
        ReportOpFailure.NO_OPEN_SESSION -> "No open report session."
        ReportOpFailure.NOTHING_TO_FLUSH -> "No report data to flush."
        ReportOpFailure.WRITE_FAILED -> "Report write failed — data was retained."
        ReportOpFailure.MATCH_NOT_FOUND -> "No report database found for that match."
        ReportOpFailure.EXPORT_FAILED -> "Report export failed."
    }
}
