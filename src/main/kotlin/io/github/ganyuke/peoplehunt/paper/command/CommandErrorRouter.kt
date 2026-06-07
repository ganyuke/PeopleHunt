package io.github.ganyuke.peoplehunt.paper.command

import io.github.ganyuke.peoplehunt.core.events.MatchEvent
import io.github.ganyuke.peoplehunt.core.events.MatchEventBus
import io.github.ganyuke.peoplehunt.core.ports.outbound.LoggerPort
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchFailureReason
import io.github.ganyuke.peoplehunt.core.services.core.models.MatchResult
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportOpFailure
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportOpResult
import io.github.ganyuke.peoplehunt.core.services.reporting.persistence.ReportSessionBlockReason
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object CommandErrorRouter {
    // errors for match engine issues
    fun handle(source: CommandSourceStack, result: MatchResult): Int = when (result) {
        is MatchResult.Ok -> {
            result.message?.let { message -> source.sender.sendMessage(Component.text(message, NamedTextColor.GREEN)) }
            1
        }
        is MatchResult.Err -> fail(source, CommandFailure.EngineReason(result.reason))
    }

    // guard against starting sessions with lingering report data
    fun handle(source: CommandSourceStack, reason: ReportSessionBlockReason?): Int = when (reason) {
        null -> 1
        else -> fail(source, CommandFailure.ReportBlockReason(reason))
    }

    // errors for report subcommand issues
    fun handle(
        source: CommandSourceStack,
        result: ReportOpResult,
        logger: LoggerPort? = null,
        outbound: MatchEventBus? = null
    ): Int = when (result) {
        is ReportOpResult.Ok -> {
            val msg = result.message ?: "Report command succeeded."
            logger?.info(msg)
            source.sender.sendMessage(Component.text(msg, NamedTextColor.GREEN))
            1
        }
        is ReportOpResult.Err -> {
            val failure = CommandFailure.ReportOpReason(result.reason)
            val text = failureText(failure)

            // for reporting that also wants logging side effects
            if (result.reason == ReportOpFailure.WRITE_FAILED || result.reason == ReportOpFailure.EXPORT_FAILED) {
                logger?.error(text, result.cause)
                outbound?.post(MatchEvent.OperatorNotification("$text: ${result.cause?.message ?: "Unknown error"}"))
            }

            fail(source, failure)
        }
    }

    sealed interface CommandFailure {
        object TooManyRunners : CommandFailure
        object NoEligibleTargets : CommandFailure
        object MustBePlayer : CommandFailure
        object NoLastMatch : CommandFailure
        data class EngineReason(val reason: MatchFailureReason) : CommandFailure
        data class ReportBlockReason(val reason: ReportSessionBlockReason) : CommandFailure
        data class ReportOpReason(val reason: ReportOpFailure) : CommandFailure // Added
    }

    fun fail(source: CommandSourceStack, reason: MatchFailureReason): Int = fail(source, CommandFailure.EngineReason(reason))

    fun fail(source: CommandSourceStack, reason: CommandFailure): Int {
        source.sender.sendMessage(Component.text(failureText(reason), NamedTextColor.RED))
        return 0
    }

    private fun failureText(failure: CommandFailure): String = when (failure) {
        CommandFailure.TooManyRunners -> "Runner selection must resolve to exactly one player."
        CommandFailure.NoEligibleTargets -> "No eligible players were selected."
        CommandFailure.MustBePlayer -> "Command can only be used by players."
        CommandFailure.NoLastMatch -> "No last match found."

        is CommandFailure.EngineReason -> when (failure.reason) {
            MatchFailureReason.ALREADY_PRIMED -> "The match is already primed."
            MatchFailureReason.ALREADY_STARTED -> "The match has already started."
            MatchFailureReason.NOT_RUNNING -> "Unable to stop match: none currently running."
            MatchFailureReason.NO_RUNNER_SPECIFIED -> "No runner specified. Use /ph runner add <player> first."
            MatchFailureReason.PLAYER_ALREADY_RUNNER -> "Cannot add target: player is already assigned as the runner."
            MatchFailureReason.PLAYER_ALREADY_HUNTER -> "Cannot add target: player is already assigned as a hunter."
            MatchFailureReason.PLAYER_NOT_IN_GROUP -> "Player is not a member of that group."
        }

        is CommandFailure.ReportBlockReason -> when (failure.reason) {
            ReportSessionBlockReason.SESSION_ALREADY_ACTIVE -> "A report session is already active."
            ReportSessionBlockReason.DATABASE_OPEN_FAILED ->
                "Report database failed to open. Run /ph report flush to try again."
            ReportSessionBlockReason.FINALIZE_PENDING ->
                "Report data from the last match was not saved. Run /ph report flush to try again or /ph report clear to discard the previous report data."
        }

        is CommandFailure.ReportOpReason -> when (failure.reason) { // Added
            ReportOpFailure.NO_OPEN_SESSION -> "No open report session."
            ReportOpFailure.NOTHING_TO_FLUSH -> "No report data to flush."
            ReportOpFailure.WRITE_FAILED -> "Report write failed. Holding data for future flush."
            ReportOpFailure.MATCH_NOT_FOUND -> "No report database found for that match."
            ReportOpFailure.EXPORT_FAILED -> "Report export failed. See server log for details."
        }
    }
}