package io.github.ganyuke.peoplehunt.paper.command

import io.github.ganyuke.peoplehunt.core.services.MatchCommandService
import io.github.ganyuke.peoplehunt.core.services.MatchCommandService.CommandMessage
import io.github.ganyuke.peoplehunt.core.services.MatchCommandService.TargetResolverPort
import io.github.ganyuke.peoplehunt.core.services.MatchEngine
import io.github.ganyuke.peoplehunt.core.services.MatchEngine.FailureReason
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.entity.Player
import kotlin.uuid.toKotlinUuid

class MatchCommand(
    private val commandService: MatchCommandService<CommandSender>,
) : TabExecutor {
    constructor(matchEngine: MatchEngine) : this(
        MatchCommandService(
            matchEngine = matchEngine,
            targetResolver = BukkitTargetResolver(),
        )
    )

    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        commandService.execute(sender, args.toList())
            .map(::render)
            .forEach(sender::sendMessage)

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): MutableList<String> =
        commandService.suggest(args.toList()).toMutableList()

    private fun render(message: CommandMessage): Component = when (message) {
        CommandMessage.Success ->
            Component.text("Success!", NamedTextColor.GREEN)

        is CommandMessage.Usage ->
            Component.text(message.text, NamedTextColor.RED)

        is CommandMessage.Error ->
            Component.text(message.text, NamedTextColor.RED)

        is CommandMessage.Failure ->
            Component.text(failureText(message.reason), NamedTextColor.RED)
    }

    private fun failureText(reason: FailureReason): String = when (reason) {
        FailureReason.ALREADY_PRIMED ->
            "The match is already primed."

        FailureReason.ALREADY_STARTED ->
            "The match has already started."

        FailureReason.NOT_RUNNING ->
            "Unable to stop match: none currently running."

        FailureReason.NO_RUNNER_SPECIFIED ->
            "No runner specified. Use /ph runner add <player> first."

        FailureReason.PLAYER_ALREADY_RUNNER ->
            "Cannot add target: player is already assigned as the runner."

        FailureReason.PLAYER_ALREADY_HUNTER ->
            "Cannot add target: player is already assigned as a hunter."

        FailureReason.PLAYER_NOT_IN_GROUP ->
            "Player is not a member of that group."
    }
}

private class BukkitTargetResolver : TargetResolverPort<CommandSender> {
    override fun resolveOnline(sender: CommandSender, token: String): List<MatchEngine.MatchPlayer> {
        val players = try {
            Bukkit.selectEntities(sender, token).filterIsInstance<Player>()
        } catch (_: IllegalArgumentException) {
            listOfNotNull(Bukkit.getPlayer(token))
        }

        return players.map { player ->
            MatchEngine.MatchPlayer(
                uuid = player.uniqueId.toKotlinUuid(),
                name = player.name,
            )
        }
    }

    override fun onlinePlayerNames(): List<String> =
        Bukkit.getOnlinePlayers().map(Player::getName)
}