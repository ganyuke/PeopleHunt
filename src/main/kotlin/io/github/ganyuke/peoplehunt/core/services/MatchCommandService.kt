package io.github.ganyuke.peoplehunt.core.services

import io.github.ganyuke.peoplehunt.core.services.MatchEngine.MatchResult
import io.github.ganyuke.peoplehunt.core.services.MatchEngine.FailureReason
import kotlin.collections.filterNot

class MatchCommandService<S>(
    private val matchEngine: MatchEngine,
    private val targetResolver: TargetResolverPort<S>,
) {
    // Port only needs to handle basic online lookups now
    interface TargetResolverPort<S> {
        fun resolveOnline(sender: S, token: String): List<MatchEngine.MatchPlayer>
        fun onlinePlayerNames(): List<String>
    }

    // UI-agnostic feedback messages returned to the platform command executor
    sealed interface CommandMessage {
        data object Success : CommandMessage
        data class Usage(val text: String) : CommandMessage
        data class Error(val text: String) : CommandMessage
        data class Failure(val reason: FailureReason) : CommandMessage
    }

    /**
     * Entrypoint for handling commands (/ph <arg0> <arg1> <arg2>)
     */
    fun execute(sender: S, args: List<String>): List<CommandMessage> {
        val root = args.getOrNull(0)?.lowercase() ?: return listOf(getGeneralUsage())

        return when (root) {
            "runner", "hunter" -> handleParticipantCommand(sender, group = root, args)
            "prime"  -> matchEngine.prime().asMessages()
            "start"  -> matchEngine.forceStart().asMessages()
            "end"    -> matchEngine.forceEnd().asMessages()
            else     -> listOf(getGeneralUsage())
        }
    }

    /**
     * Handles /ph <runner|hunter> <add|remove|clear> <target>
     */
    private fun handleParticipantCommand(sender: S, group: String, args: List<String>): List<CommandMessage> {
        val action = args.getOrNull(1)?.lowercase()

        // 1. Handle Clear Action Immediate Return
        if (action == "clear") {
            return when (group) {
                "runner" -> matchEngine.clearRunner()
                "hunter" -> matchEngine.clearHunters()
                else -> throw IllegalStateException()
            }.asMessages()
        }

        if (action != "add" && action != "remove") {
            return listOf(CommandMessage.Usage("Usage: /ph $group <add|remove|clear>"))
        }

        val targetToken = args.getOrNull(2)
            ?: return listOf(CommandMessage.Usage("Usage: /ph $group $action <player|selector>"))

        // 2. Handle REMOVE Action (Look up matching tracked player in the current match session)
        if (action == "remove") {
            val targetPlayer = when (group) {
                "runner" -> matchEngine.getRunner()?.takeIf { it.name.equals(targetToken, ignoreCase = true) }
                "hunter" -> matchEngine.getHunters().find { it.name.equals(targetToken, ignoreCase = true) }
                else -> null
            } ?: return listOf(CommandMessage.Failure(FailureReason.PLAYER_NOT_IN_GROUP))

            return when (group) {
                "runner" -> matchEngine.removeRunner(targetPlayer)
                "hunter" -> matchEngine.removeHunter(targetPlayer)
                else -> throw IllegalStateException()
            }.asMessages()
        }

        // 3. Handle ADD Action (Requires looking up online players)
        val onlineTargets = targetResolver.resolveOnline(sender, targetToken)
        if (onlineTargets.isEmpty()) {
            return listOf(CommandMessage.Error("No online players found matching target."))
        }

        if (group == "runner" && onlineTargets.size != 1) {
            return listOf(CommandMessage.Error("Runner target must resolve to exactly one player."))
        }

        val currentRunner = matchEngine.getRunner()

        // Run targets through match engine filtering rules (e.g. @a shouldn't add the runner as a hunter)
        val eligibleTargets = onlineTargets.filterNot { target ->
            group == "hunter" && targetToken == "@a" && target.uuid == currentRunner?.uuid
        }

        if (eligibleTargets.isEmpty()) {
            return listOf(CommandMessage.Error("No eligible targets matched."))
        }

        return eligibleTargets.map { player ->
            when (group) {
                "runner" -> matchEngine.setRunner(player)
                "hunter" -> matchEngine.addHunter(player)
                else -> throw IllegalStateException()
            }.asMessage()
        }
    }

    /**
     * Handles tab completion rules based on arguments length
     */
    fun suggest(args: List<String>): List<String> = when (args.size) {
        1 -> listOf("runner", "hunter", "prime", "start", "end").matching(args[0])
        2 -> {
            if (args[0] == "runner" || args[0] == "hunter") {
                listOf("add", "remove", "clear").matching(args[1])
            } else emptyList()
        }
        3 -> {
            val group = args[0].lowercase()
            val action = args[1].lowercase()
            val input = args[2]

            val candidates = when (action) {
                "add" -> listOf("@s", "@a", "@p") + targetResolver.onlinePlayerNames()
                "remove" -> when (group) {
                    "runner" -> listOfNotNull(matchEngine.getRunner()?.name)
                    "hunter" -> matchEngine.getHunters().map { it.name }
                    else -> emptyList()
                }
                else -> emptyList()
            }
            candidates.matching(input)
        }
        else -> emptyList()
    }

    // --- Helper Utilities ---

    private fun getGeneralUsage() = CommandMessage.Usage("Usage: /ph <runner|hunter|prime|start|end>")

    private fun List<String>.matching(input: String): List<String> =
        filter { it.startsWith(input, ignoreCase = true) }

    private fun MatchResult.asMessages(): List<CommandMessage> = listOf(asMessage())

    private fun MatchResult.asMessage(): CommandMessage = when (this) {
        MatchResult.Ok -> CommandMessage.Success
        is MatchResult.Err -> CommandMessage.Failure(reason)
    }
}