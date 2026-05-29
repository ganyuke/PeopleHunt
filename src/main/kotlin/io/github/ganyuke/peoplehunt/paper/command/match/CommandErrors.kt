package io.github.ganyuke.peoplehunt.paper.command.match

import io.github.ganyuke.peoplehunt.core.services.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.MatchEngine.FailureReason
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor

object CommandErrors {
    fun handle(source: CommandSourceStack, result: MatchEngine.MatchResult): Int = when (result) {
        is MatchEngine.MatchResult.Ok -> {
            result.message?.let { message -> source.sender.sendMessage(Component.text(message, NamedTextColor.GREEN)) }
            1
        }

        is MatchEngine.MatchResult.Err -> fail(source, CommandFailure.EngineReason(result.reason))
    }

    sealed interface CommandFailure {
        object TooManyRunners : CommandFailure
        object NoEligibleTargets : CommandFailure
        object  MustBePlayer : CommandFailure
        data class EngineReason(val reason: FailureReason) : CommandFailure
    }

    fun fail(source: CommandSourceStack, reason: FailureReason): Int = fail(source, CommandFailure.EngineReason(reason))

    fun fail(source: CommandSourceStack, reason: CommandFailure): Int {
        source.sender.sendMessage(Component.text(failureText(reason), NamedTextColor.RED))
        return 0 // zero means bad execution in Brigadier
    }

    private fun failureText(failure: CommandFailure): String = when (failure) {
        CommandFailure.TooManyRunners -> "Runner selection must resolve to exactly one player."
        CommandFailure.NoEligibleTargets -> "No eligible players were selected."
        CommandFailure.MustBePlayer -> "Command can only be used by players."
        is CommandFailure.EngineReason -> when (failure.reason) {
            FailureReason.ALREADY_PRIMED -> "The match is already primed."
            FailureReason.ALREADY_STARTED -> "The match has already started."
            FailureReason.NOT_RUNNING -> "Unable to stop match: none currently running."
            FailureReason.NO_RUNNER_SPECIFIED -> "No runner specified. Use /ph runner add <player> first."
            FailureReason.PLAYER_ALREADY_RUNNER -> "Cannot add target: player is already assigned as the runner."
            FailureReason.PLAYER_ALREADY_HUNTER -> "Cannot add target: player is already assigned as a hunter."
            FailureReason.PLAYER_NOT_IN_GROUP -> "Player is not a member of that group."
        }
    }

}